# WORK-025 - Simple delivery API

Base: `/api/v1/fulfillment`. JWT required. JSON responses use existing `ApiResponse`.
This slice works with confirmed stock-out records; it does not create stock-outs or payments.

## Accountant

- `GET /deliveries`: paginated list; optional status, shipperId, orderId, deliveryCode (exact),
  scheduledFrom/To (yyyy-MM-dd). Dates use Asia/Ho_Chi_Minh. Default size 20, maximum 100.
- `PUT /delivery-assignments`: assign/reassign before pickup, all records in one transaction.
  Each record must be DISPATCHED/ASSIGNED, confirmedAt non-null, startedAt null.
- `DELETE /deliveries/{id}/assignment`: unassign before pickup; return to DISPATCHED.
- `GET /orders/{orderId}/deliveries`: delivery batches of an order; empty when none exist.
  This endpoint does not assert the Sales order exists.

Assignment body (1..100 unique delivery IDs):

```json
{
  "shipperId": "UUID",
  "deliveryIds": ["UUID"]
}
```

IAM `/api/v1/iam/users` currently supports roleId/status filters but is SHOP_OWNER-only.
ACCOUNTANT needs an IAM-owned read contract for the picker. No IAM permission changes here.

## Shipper

- `GET /me/deliveries`: only records assigned to the authenticated shipper;
  optional status, scheduledFrom/To and pagination. No client-supplied shipperId.
- `POST /deliveries/{id}/start`: no body; ASSIGNED -> IN_TRANSIT. Replay preserves startedAt.
- `POST /deliveries/{id}/complete`: multipart/form-data, 1..5 files named `photos`, <=5 MiB each.
  Existing global configuration also caps the entire multipart request at 7MB and each file at 6MB.
  Image bytes must match the declared image MIME and have a supported decoder.
  No separate signature field or signature recognition. Success response is Summary.
- `POST /deliveries/{id}/fail`: JSON reason, trimmed nonblank, <=1000 characters;
  append to existing note without clearing it. No automatic stock return.

```json
{
  "reason": "Recipient could not be contacted"
}
```

FAILED cannot be reset/retried through this slice. Multiple batches use separate Delivery rows
with batchNo; no per-attempt history. Success means the whole batch was handed over.

## Shared detail and photos

- `GET /deliveries/{id}`: ACCOUNTANT or the assigned SHIPPER. `data` contains delivery summary,
  assignedBy, shipperName/Phone, items and photos (photoId/contentUrl).
- `GET /deliveries/{id}/photos/{photoId}`: same object-level permission, photo must belong to
  that delivery. Returns image bytes with Cache-Control: no-store and nosniff.
  Storage keys are never exposed through HTTP.

## States and errors

DISPATCHED means stock-out confirmed; ASSIGNED means assigned; IN_TRANSIT means picked up;
DELIVERED means handed over with photos; FAILED means unsuccessful. DRAFT/CANCELLED are read-only.
Delivery completion does not change order/payment status, stock, or COD cash records.

- FUL_001 / 409: invalid state transition.
- FUL_002 / 400: target user is not an active shipper.
- FUL_003 / 400: missing/invalid/oversized images or unsupported decoder.
- FUL_004 / 503: photo storage unavailable; delivery is not marked DELIVERED.
- AUTH_001 / 401, AUTH_002 / 403, SYS_004 / 404: authentication, permission, missing record.
- Existing exception handler handles validation/multipart framework errors.

## Storage integration

DeliveryPhotoStorage is a store/read/delete port. The default bean fails with FUL_004.
There is no real Cloudinary adapter, mock URL or database image bytes.
JPEG/PNG have JDK decoders; WebP requires an installed runtime ImageIO decoder or is safely rejected.
The real adapter must support private reads and deletion of uploads orphaned by a failed transaction
or a concurrent completion. Cleanup failures are logged for storage reconciliation.

Photo completion cannot be accepted end-to-end before real storage is integrated. Central audit,
UC39, Sales aggregation and COD/warranty integration remain separate owner dependencies.

## 2026-11-12 — Owner-approved temporary local photo storage

This decision supersedes the unavailable-default storage statements above. Delivery photos remain
mandatory. The default adapter now saves real image files under `./uploads/delivery-photos`, ignored
by Git, and serves them only through the existing authorized photo API. Override the directory with
`aquasuite.delivery.photo-directory` (environment: `AQUASUITE_DELIVERY_PHOTO_DIRECTORY`). Keep this
directory across backend restarts/deployments; database metadata alone cannot restore missing files.
An external DeliveryPhotoStorage bean can replace the local adapter later. No public static file URL,
Cloudinary dependency, database table, order/payment mutation, or automatic stock return is added.
`batch_no` continues to identify delivery batches. Failed delivery stock returns require the warehouse
workflow to receive/inspect and confirm the actual returned quantities.

## 2026-11-14 — Delivery result handoff

Summary also exposes confirmedAt and dispatchDate from existing Delivery fields for progress display;
no schema change. Creation uses an existing ISSUED invoice only (see UC39 contract).
Customer refusal is recorded through the failure endpoint as a mandatory reason. FAILED is terminal
for this batch; it does not restock, cancel/refund the order or release already consumed reservations.
UC37 owns returned-goods inspection, restocking and quarantine; UC64 owns refunds.
Successful handover requires photos and does not automatically complete/pay the whole order.
PostgreSQL tests cover issue -> assignment -> start -> refusal/failure or photo completion,
retry safety, and unchanged order/payment state after recording delivery results.

## 2026-11-14 — Temporary obstacles and retry on the same delivery

Supersedes earlier guidance that every unsuccessful visit becomes FAILED. Temporary obstacles
(customer unavailable, no contact, inaccessible site, changed schedule) use RESCHEDULED, displayed
as "Chờ giao lại". This is a Delivery state; it does not cancel or change the Sales order status.

- POST /deliveries/{id}/reschedule: assigned SHIPPER, body {"reason":"...","scheduledDate":null}
  or a future ISO offset timestamp. Reason required, max1000 characters. Allowed from ASSIGNED/IN_TRANSIT;
  preserve invoice, items, stock consumption, COD and original pickup time. Append reason to note.
- Same request while RESCHEDULED is a replay. If initially no new date was known, the assigned shipper
  can submit a future date once; changed requests against an already fixed date conflict. After another
  start/visit, a further obstacle can create another RESCHEDULED transition on the same delivery.
- Existing PUT /delivery-assignments permits ACCOUNTANT/SHOP_OWNER to reassign RESCHEDULED deliveries,
  including after initial pickup. Retain RESCHEDULED and first startedAt; update shipper/assignedBy/At.
  Physical handover to the new shipper is operationally required; no inventory movement is generated.
- POST /deliveries/{id}/start resumes RESCHEDULED -> IN_TRANSIT for the currently assigned shipper;
  preserve first pickup timestamp if present. Old shipper access is denied after reassignment.
- Completing directly from RESCHEDULED is blocked; resume first. COD claims still require DELIVERED.
- POST /deliveries/{id}/fail now requires {"reason":"...","customerCancellationConfirmed":true}.
  Missing/false confirmation is rejected. This action is reserved for confirmed cancellation/refusal,
  including while RESCHEDULED; it records terminal FAILED, not automatic order cancellation/restock/refund.
  UC37 handles returned goods and UC64 handles money. Legacy FAILED records are not reclassified.

No new table or migration: delivery_status already uses VARCHAR and scheduled_date/note already exist.
