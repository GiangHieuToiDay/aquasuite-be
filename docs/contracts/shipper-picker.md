# Shipper search for assignment

GET /api/v1/iam/users/shippers?keyword=&page=0&size=20

ACCOUNTANT and SHOP_OWNER may call this endpoint. It reuses UserAccountService.search with the
canonical SHIPPER role and ACTIVE status fixed on the server. Clients cannot change that scope.
Response uses the existing ApiResponse<AccountPageResponse>: data.content, page, size,
totalElements, totalPages. Use each user's id as shipperId for assignment.
Optional keyword uses the existing username/email/phone search; max100characters. Page>=0,
size1..100 (default20), stable username/id order.
Only one method is added to UserAccountController; no separate controller/service/repository/DTO.
All existing account management APIs remain SHOP_OWNER-only. No schema or data change.
