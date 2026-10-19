# Spring Boot 3.5 Migration Note (FEATURE-004)

## 1. Migration Overview

- **Feature ID**: `FEATURE-004`
- **Scope**: `BACKEND_ONLY`
- **Previous Baseline**: Spring Boot `4.1.1` (Spring Framework 7.0.9, Spring Security 7.1.1, Spring Data 2026.0.1, Hibernate 7.4.5.Final, Jackson 3.1.5, Jakarta Validation 3.1.1, JUnit Jupiter 6.0.3, Java 21)
- **Target Baseline**: Spring Boot `3.5.16` (Spring Framework 6.2.19, Spring Security 6.5.11, Spring Data 2025.0.13, Hibernate 6.6.53.Final, Jackson 2.21.4, Jakarta Validation 3.0.2, JUnit Jupiter 5.12.2, Java 21)
- **Database Engine**: PostgreSQL (`postgresql:42.7.11` runtime scope do Spring Boot 3.5.16 BOM quản lý)
- **Rationale**: Đưa technology baseline về phiên bản ổn định được hỗ trợ đầy đủ (`3.5.x`) và giải quyết việc thiếu hụt các test dependencies trên Maven Central từ Boot 4.

## 2. Managed Dependency Versions

Tất cả các thành phần cốt lõi được quản lý tập trung thông qua `spring-boot-starter-parent:3.5.16` (không khai báo explicit version/BOM override riêng cho Spring Security, Spring Framework hoặc Jackson):

| Dependency / Component | Managed Version | Scope | Source |
|---|---|---|---|
| `spring-boot-starter-parent` | `3.5.16` | - | Root POM parent |
| `spring-framework` | `6.2.19` | compile | Boot 3.5.16 BOM |
| `spring-security-core` | `6.5.11` | compile | Boot 3.5.16 BOM |
| `spring-data-jpa` / `spring-data-jdbc` | `3.5.13` (BOM `2025.0.13`) | compile | Boot 3.5.16 BOM |
| `hibernate-core` | `6.6.53.Final` | compile | Boot 3.5.16 BOM |
| `postgresql` | `42.7.11` | runtime | Boot 3.5.16 BOM |
| `jackson-databind` | `2.21.4` (BOM `2.21.4`) | compile | Boot 3.5.16 BOM |
| `jakarta.validation-api` | `3.0.2` | compile | Boot 3.5.16 BOM |
| `junit-jupiter` | `5.12.2` | test | Boot 3.5.16 BOM |
| `maven-compiler-plugin` | `3.14.1` | build | Boot 3.5.16 Parent |
| `maven-surefire-plugin` | `3.5.6` | build | Boot 3.5.16 Parent |

## 3. Compatibility Adjustments

1. **`pom.xml` Dependency Updates**:
   - Chuyển `parent.version` từ `4.1.1` sang `3.5.16`.
   - Loại bỏ hai test starters chỉ có trên Boot 4: `spring-boot-starter-data-jdbc-test` và `spring-boot-starter-data-jpa-test`.
   - Thêm `spring-boot-starter-test` (phiên bản do parent quản lý, scope `test`).
   - Giữ nguyên `<java.version>21</java.version>` và custom Lombok annotation processor paths.
2. **Auto-Configuration Package Strings**:
   - Trong `SpringProfileConfigDataTest.java`: Cập nhật hai class name loại trừ auto-configuration:
     - `org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration` -> `org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration`
     - `org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration` -> `org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration`
3. **Source Code**:
   - Không có thay đổi nào trong `src/main/java/` (toàn bộ production classes tương thích hoàn toàn với Spring Framework 6.2.x và Spring Security 6.5.x).
   - Giữ nguyên package `com.aquasuite.ims`, `ApiResponse<T>`, `GlobalExceptionHandler` và profile YAML configs (`application*.yml`).

## 4. Verification Results

| Check / Command | Exit Code | Kết quả | Ghi chú |
|---|---|---|---|
| `.\mvnw.cmd dependency:resolve` | `0` | PASS | Toàn bộ dependencies và transitive artifacts được resolve từ Maven Central |
| `.\mvnw.cmd help:effective-pom` | `0` | PASS | Xác nhận parent 3.5.16, compiler 3.14.1, surefire 3.5.6 |
| `.\mvnw.cmd dependency:tree` | `0` | PASS | Đồ thị phụ thuộc thuần nhất: Framework 6.2.19, Security 6.5.11, Jackson 2.21.4 |
| `.\mvnw.cmd clean compile test-compile` | `0` | PASS | Compile thành công với Java 21 release flag |
| `.\mvnw.cmd -Dtest=SpringProfileConfigDataTest test` | `0` | PASS | 5/5 tests pass (default, dev, test, prod, external override) |
| `.\mvnw.cmd -Dtest=GlobalExceptionHandlerTest test` | `0` | PASS | 4/4 tests pass (xử lý exception và response contract không đổi) |
| `.\mvnw.cmd -DskipTests package` | `0` | PASS | Đóng gói thành công `aquasuite-0.0.1-SNAPSHOT.jar` executable archive |
| `.\mvnw.cmd test` | `1` | 9 PASS, 1 KNOWN_BASELINE_FAILURE | 10 tests run: 9 passed, 0 failures, 1 error (`contextLoads`) |
| Isolated PostgreSQL Connectivity | - | `NOT_RUN_ENVIRONMENT_UNAVAILABLE` | Không có môi trường database test cô lập / credentials |

## 5. Known Baseline Limitation

- **`AquasuiteBeApplicationTests.contextLoads`**: Lỗi `BeanCreationException` do `Failed to determine a suitable driver class` (thiếu cấu hình datasource URL/credential khi khởi động full context). Đây là lỗi tồn tại từ baseline trước khi migration và không phải lỗi hồi quy (regression) của `FEATURE-004`.
