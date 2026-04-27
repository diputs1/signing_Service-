# Dự án Ký Số Tập Trung (Signing Service)

Dự án cung cấp các dịch vụ ký số tập trung, hỗ trợ ký số trên các định dạng tài liệu khác nhau (như PDF) thông qua nhiều hình thức (ký HSM, ký USB Token). Hệ thống được xây dựng dựa trên framework Spring Boot và tích hợp thư viện lõi DSS (Digital Signature Service).

## 🚀 Công nghệ sử dụng
- **Ngôn ngữ:** Java 17
- **Framework:** Spring Boot 3.3.4
- **Thư viện Ký Số:** DSS Framework (eu.europa.ec.joinup.sd-dss), PDFBox, Aspose Words
- **Cơ sở dữ liệu:** SQL Server
- **Cache & Message Broker:** Redis
- **Containerization:** Docker & Docker Compose
- **CI/CD:** GitLab CI

## 🏗 Cấu trúc Dự án

Dự án được chia thành các module chính (Maven Multi-module):

- `signing-common`: Các class tiện ích (utils), constants, DTO, các cấu hình dùng chung.
- `signing-core`: Chứa logic nghiệp vụ lõi về kết nối thiết bị ký (USB Token, HSM, API), xử lý chuẩn ký số (PAdES, v.v.).
- `signing-web-service`: Ứng dụng cung cấp các API RESTful để thực hiện nghiệp vụ ký số, xác thực, và giao tiếp với hệ thống bên ngoài.
- `signing-desktop`: Module cung cấp các tính năng chạy dưới dạng Desktop App (ví dụ để giao tiếp với USB Token ở máy Client).

## ⚙️ Yêu cầu Hệ thống

- Java Development Kit (JDK) 17
- Maven 3.8+
- Docker & Docker Compose (Nếu chạy qua container)
- SQL Server
- Redis 7+

## 🛠 Hướng dẫn Cài đặt & Chạy ứng dụng

### 1. Chạy trên môi trường Local (Phát triển)

**Bước 1: Cấu hình Môi trường**
Đảm bảo đã cài đặt Redis và cấu hình kết nối tới SQL Server. Cập nhật các thông tin kết nối Database và Redis trong file cấu hình `signing-web-service/src/main/resources/application.yml`.

**Bước 2: Cấu hình USB Token (Nếu cần test ký Token Local)**
Định cấu hình đường dẫn tới thư viện driver của USB Token (VD: `.dll` cho Windows) trong file `token-config.json` nằm tại thư mục gốc.

**Bước 3: Build & Run**
Có thể build và chạy project bằng Maven wrapper có sẵn:

```bash
./mvnw clean install -DskipTests
cd signing-web-service
../mvnw spring-boot:run
```
Web service sẽ khởi chạy tại port mặc định: `6868` (URL Healthcheck: `http://localhost:6868/actuator/health`).

### 2. Chạy bằng Docker Compose

Hệ thống có cấu hình sẵn `Dockerfile` và `docker-compose.yml` để dễ dàng triển khai cùng Redis:

```bash
# Build image và khởi chạy container (chạy nền)
docker-compose up -d --build
# Xem log để kiểm tra trạng thái khởi động
docker-compose logs -f kyso-service
```
Lưu ý: Các thông tin như `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` cần được cấu hình đúng với Database thực tế của bạn trong file `docker-compose.yml`.
## 🗄 Triển khai CI/CD

Dự án cấu hình GitLab CI để tự động hóa quá trình deploy. File `.gitlab-ci.yml` định nghĩa bước tự động build và copy file `.jar` ra thư mục deploy và restart service (`signing-service`) trên server (yêu cầu cấu hình gitlab-runner với tag `kyso`).

## 📜 Nhật ký ứng dụng (Logging)

- Log ứng dụng và các dịch vụ liên quan (ví dụ: OTP) được sinh ra mặc định tại thư mục `logs/` trong thư mục gốc.
- Các tập tin log tiêu biểu: `logs/otp-service.log`.
