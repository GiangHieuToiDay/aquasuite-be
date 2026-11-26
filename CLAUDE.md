# Hướng dẫn Claude Code — AquaSuite Backend

Quy tắc dự án dùng chung với Codex nằm ở `AGENTS.md` và được import nguyên văn bên dưới. Không lặp lại hoặc ghi đè các quy tắc đó tại đây.

@AGENTS.md

## Graphify trong Claude Code

- Áp dụng mục **Graphify Rules** trong `AGENTS.md`: dùng graph chung `graphify-out/`, ưu tiên truy vấn graph cho tìm kiếm code, kiến trúc, lập kế hoạch, audit, review và phạm vi ảnh hưởng, rồi xác minh lại bằng source code và contract thực tế.
- Trong Claude Code có thể dùng `/graphify query "<câu hỏi>"`, `/graphify path "A" "B"`, `/graphify explain "X"` hoặc CLI `graphify query|path|explain`.
- Cập nhật incremental bằng skill `/graphify src/main --update` từ repository root. Không chạy CLI `graphify update` trực tiếp vì có thể tạo output lồng trong `src/main/graphify-out/`; không chạy full build `/graphify src/main` nếu graph hiện có còn dùng được.
