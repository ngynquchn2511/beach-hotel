# Hướng dẫn đưa website lên Oracle Cloud (miễn phí, chạy 24/7, không mất dữ liệu)

Kết quả: website chạy liên tục trên máy ảo miễn phí của Oracle, có HTTPS, dữ liệu MySQL và ảnh upload nằm trên ổ đĩa máy ảo nên khởi động lại hay deploy lại đều không mất.

## Bước 1. Tạo tài khoản Oracle Cloud

1. Vào https://www.oracle.com/cloud/free/ và bấm "Start for free".
2. Điền thông tin, chọn **Home Region** gần Việt Nam (Singapore hoặc Tokyo). Không đổi được vùng sau này.
3. Xác minh bằng thẻ Visa/Mastercard (Oracle chỉ tạm giữ một khoản nhỏ rồi hoàn lại; tài nguyên Always Free không bị tính tiền).

## Bước 2. Tạo máy ảo

1. Menu → Compute → Instances → **Create instance**.
2. Image: **Ubuntu 22.04**. Shape: **Ampere (VM.Standard.A1.Flex)**, 2 OCPU, 12 GB RAM (nằm trong hạn mức miễn phí).
3. Mục "Add SSH keys": chọn "Generate a key pair" và **tải cả hai file khóa về máy** (mất là không vào được máy ảo).
4. Bấm Create, đợi trạng thái Running, ghi lại **Public IP address**.

Nếu báo "Out of capacity", thử lại sau vài giờ hoặc giảm còn 1 OCPU, 6 GB RAM.

## Bước 3. Mở cổng 80 và 443

1. Trên web Oracle: trang instance → Subnet → Security List → **Add Ingress Rules**: Source CIDR `0.0.0.0/0`, TCP, Destination port `80,443`.
2. Bên trong máy ảo (Ubuntu của Oracle chặn sẵn bằng iptables), chạy ở bước 4 sau khi SSH vào:

```bash
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
sudo netfilter-persistent save
```

## Bước 4. Cài Docker và chạy website

SSH vào máy ảo (Windows dùng PowerShell):

```bash
ssh -i duong-dan/ssh-key.key ubuntu@<PUBLIC_IP>
```

Trong máy ảo:

```bash
# Cai Docker
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu && newgrp docker

# Lay ma nguon
git clone <link-repo-github-cua-ban>/beach-hotel.git
cd beach-hotel/deploy

# Cau hinh
cp .env.example .env
nano .env        # dien DOMAIN = <PUBLIC_IP>.sslip.io, cac mat khau, ...

# Chay (lan dau mat vai phut de build)
docker compose up -d --build
docker compose logs -f app     # thay "Started HotelBookingApplication" la xong, Ctrl+C de thoat
```

Mở trình duyệt: `https://<PUBLIC_IP>.sslip.io` (trang quản trị: `/admin`).

## Cập nhật phiên bản mới

```bash
cd ~/beach-hotel && git pull
cd deploy && docker compose up -d --build app
```

Dữ liệu và ảnh không bị ảnh hưởng.

Nếu phiên bản mới có file trong thư mục `sql/` và app đang chạy với `DDL_AUTO=validate`, chạy các file đó **trước** lệnh build ở trên (với `DDL_AUTO=update` như `docker-compose.yml` mặc định thì Hibernate tự thêm cột, không bắt buộc). Các file viết để chạy lại nhiều lần vẫn an toàn:

```bash
cd ~/beach-hotel/deploy
for f in ../sql/*.sql; do
  docker compose exec -T db sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" beach_hotel' < "$f" && echo "OK $f"
done
```

## Sao lưu cơ sở dữ liệu (nên làm)

Sao lưu tự động mỗi đêm lúc 2 giờ, giữ 14 bản gần nhất:

```bash
mkdir -p ~/backups
crontab -e
# them dong:
0 2 * * * cd ~/beach-hotel/deploy && docker compose exec -T db sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" beach_hotel' | gzip > ~/backups/hb_$(date +\%F).sql.gz && ls -t ~/backups/hb_*.sql.gz | tail -n +15 | xargs -r rm
```

## Lưu ý

- Oracle có thể thu hồi máy ảo Always Free nếu máy **gần như không dùng tới trong 7 ngày** (CPU rất thấp). Website có người truy cập thường không bị ảnh hưởng; để chắc chắn, có thể nâng tài khoản lên Pay As You Go (vẫn không tốn phí khi chỉ dùng tài nguyên Always Free).
- Không commit file `deploy/.env` lên GitHub (đã có trong `.gitignore`).
