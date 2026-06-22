# ⛏️ MMOBlock

**MMOBlock** là plugin Minecraft (Paper/Spigot) cho phép tạo các **block tương tác kiểu RPG** — node khoáng sản, vật thể đào được, hoặc bất kỳ block "đặc biệt" nào với máu riêng, công cụ yêu cầu, hologram động, hiệu ứng respawn theo thời gian, và hỗ trợ cả block vanilla lẫn model custom từ **ItemsAdder**.

---

## ✨ Tính năng chính

- **Block có máu (health-block)** — không vỡ ngay 1 nhát, cần đánh đủ số lần/đủ công cụ mới "chết".
- **Yêu cầu công cụ theo cấp** — định nghĩa công cụ nào được phép đào, giảm độ bền bao nhiêu, cần click bao nhiêu lần.
- **Hologram động nhiều lớp** — hiển thị tên block, thanh progress bar, đếm ngược thời gian respawn, hỗ trợ MiniMessage (gradient, màu hex) và legacy color code.
- **Hitbox tùy chỉnh qua Interaction entity** — không bị giới hạn bởi hitbox block vanilla, chỉnh được kích thước và offset riêng.
- **Hỗ trợ ItemsAdder** — hiển thị model 3D custom thay cho block thường, dùng ArmorStand ẩn mang model trên đầu.
- **Hệ thống Group** — đặt ngẫu nhiên 1 block trong nhóm định nghĩa trước (`/mmoblock placegroup`), tiện cho việc tạo node khoáng sản đa dạng trong cùng khu vực.
- **Cấu trúc nhiều block (struct)** — chọn vùng block bất kỳ trong thế giới, lưu lại thành file template, dùng để dựng nhanh cấu trúc lớn (mỏ đá, hang động...) ở nơi khác.
- **Lưu trữ bền vững bằng SQLite** — toàn bộ block đã đặt, group, thời gian respawn được lưu lại, sống sót qua restart server.
- **GUI quản lý** — xem danh sách toàn bộ block đã đặt trên server, xóa nhanh ngay trong giao diện.

---

## 📋 Yêu cầu

- Server Paper **1.20+** (dùng entity `Interaction` và `Display` — chỉ có ở Paper/Spigot bản mới)
- (Tùy chọn) [ItemsAdder](https://www.spigotmc.org/resources/itemsadder.73355/) — nếu muốn dùng model 3D custom thay block vanilla

---

## 📦 Cài đặt

1. Thả file `.jar` vào thư mục `plugins/`.
2. Khởi động lại server. Plugin sẽ tự tạo:
   - `plugins/MMOBlock/config.yml`
   - `plugins/MMOBlock/blocks/` — chứa file cấu hình từng loại block
   - `plugins/MMOBlock/structures/` — chứa các cấu trúc đã lưu
   - `plugins/MMOBlock/cache.db` — database SQLite lưu vị trí block đã đặt
3. Dùng `/mmoblock create <id>` để tạo block đầu tiên, chỉnh sửa file `.yml` tương ứng, rồi `/mmoblock reload`.

---

## 🎮 Danh sách lệnh

| Lệnh | Mô tả |
|---|---|
| `/mmoblock create <id>` | Tạo file cấu hình mẫu cho block mới tại `blocks/<id>.yml` |
| `/mmoblock place <id>` | Đặt block tại vị trí đang đứng theo cấu hình `<id>` |
| `/mmoblock placegroup <groupId>` | Đặt ngẫu nhiên 1 block thuộc nhóm `<groupId>` |
| `/mmoblock list` | Mở GUI xem toàn bộ block đã đặt trên server, click để xóa |
| `/mmoblock saveitem <id>` | Lưu item đang cầm trên tay vào database (dùng làm phần thưởve/drop) |
| `/mmoblock struct edit [shape] [size]` | Bật chế độ chọn vùng block (`shape`: single/sphere/solid, mặc định size 1) |
| `/mmoblock struct save <id>` | Lưu vùng đã chọn thành cấu trúc `<id>` |
| `/mmoblock struct remove <id>` | Xóa file cấu trúc `<id>` |
| `/mmoblock struct undo` | Hoàn tác thao tác chọn gần nhất (tối đa 10 lần) |
| `/mmoblock reload` | Reload toàn bộ config, ngôn ngữ, group, hologram |

### Cách dùng chế độ Struct (chọn vùng nhiều block)

```
/mmoblock struct edit single       → chọn từng block lẻ
/mmoblock struct edit sphere 3     → chọn theo hình cầu bán kính 3
/mmoblock struct edit solid 5      → chọn theo khối đặc cạnh 5
```

Sau khi bật chế độ edit:
- **Click trái** → chọn block (biến tạm thành AIR để xem trước)
- **Shift + Click trái** → chọn 1 block đơn lẻ tại vị trí click
- **Shift + Click phải** → hoàn tác (undo)
- **Click phải** (không shift) → bỏ chọn vùng đó

Sau khi chọn xong: `/mmoblock struct save <id>` để lưu lại, các block đã chọn sẽ tự khôi phục vật liệu gốc.

---

## ⚙️ Cấu hình một block (`blocks/<id>.yml`)

```yaml
#config file version 2.8-latest
my_iron_node:
  send-title: "&eYour tool not matching! %progress%"
  send-subtitle: "<red>or Tools are too low</red>"
  click_cooldown: 0.5      # giây giữa 2 lần click liên tiếp
  action_bar: false        # hiện thông báo qua action bar hay title
  time-refund: 5           # giây hoàn lại tiến trình nếu dừng đào giữa chừng
  respawn: 4                # giây trước khi block respawn sau khi "chết"
  break-block: false        # true = phá hủy block thật sau khi hết máu
  health-block: 5            # số "máu" (số lần đào cần để hoàn thành)
  death_delay: 1             # giây delay trước khi chuyển sang trạng thái respawn

  hitbox:
    width: 1
    length: 1
    height: 1
    # offset_x / offset_y / offset_z (tùy chọn) — lệch hitbox khỏi tâm block

  block-settings:
    enabled: true
    material: "DRAGON_EGG"   # block hiển thị ngoài đời (nếu không dùng ItemsAdder)

  block-itemsadder:
    enabled: false
    model-id: 10043
    material: paper          # vật liệu nền để áp custom model data

  hologram:
    enable: true
    height: 1.5               # độ cao gốc của cụm hologram
    clickHeight: 1.5
    respawnHeigth: 1.5
    shadowed: true
    background: false
    customHolo:                # hiển thị khi block ở trạng thái bình thường
      '1':
        value: <color:#35FF0A>Tutorial Mining</color>
        height: 0.0
      '2':
        value: <gradient:#FFFFFF:#FFFFFF>No requirement</gradient>
        height: 0.2
      '3':
        value: <color:#AAAAAA>Left-Click to mine</color>
        height: 0.2
      '4':
        value: item              # dòng đặc biệt: hiển thị model item lơ lửng
        id: WOODEN_PICKAXE
        custom_model_data: 0
        height: 1.5
    progressHolo:               # hiển thị khi đang đào
      "1":
        value: <color:#AAAAAA>Left-Click to receive &7CobbleStone</color>
        height: 0.4
      "2":
        value: <color:#AAAAAA>Right-Click to receive &7Cobble Fragment</color>
        height: 0.2
      "3":
        value: "&7[&f%progress_bar%&7]"
        height: 0.4
    deathHolo:                  # hiển thị khi đang chờ respawn
      "1":
        value: <color:#FFF800>Respawning</color>
        height: 0.4
      "2":
        value: "<gradient:#FFA500:#F92986>Respawning in: %respawnTime%s</gradient>"
        height: 0.4

  sounds:
    onClick: block.stone.break
    onDeath: block.stone.break

  drop-options:
    Lootsplosion: true          # true = item rơi tung tóe, false = rơi gọn tại tâm
    ItemGlow:
      enable: false

  allowed_tools:
    "1":
      material: WOODEN_PICKAXE
      left_click:
        decreaseDurability: 10   # giảm độ bền công cụ mỗi lần click
        clickNeeded: 3            # số lần click cần để hoàn thành với công cụ này
      allowedDrops:
        - example_drop

  drops:
    example_drop:
      item:
        material: COBBLESTONE
        total: [1-3]               # số lượng rơi ra, random trong khoảng
        chances: 1.0                # tỉ lệ rơi (1.0 = 100%)
        drop_type: center_ground    # center_ground | (các kiểu khác nếu hỗ trợ)
      target: left_click            # loại click kích hoạt drop này
```

### Placeholder dùng trong hologram

| Placeholder | Dùng ở | Ý nghĩa |
|---|---|---|
| `%progress%` | `send-title`, `send-subtitle` | Tiến trình đào hiện tại |
| `%progress_bar%` | `progressHolo` | Thanh progress bar dạng ký tự (cấu hình ở `config.yml` chính, mục `progressbar`) |
| `%respawnTime%` | `deathHolo` | Số giây còn lại trước khi block respawn |

Hỗ trợ cả **legacy color code** (`&a`, `§a`) và **MiniMessage** (`<color:#hex>`, `<gradient:#a:#b>`) trong cùng một dòng text.

### `config.yml` chính (progress bar)

```yaml
progressbar:
  progressing: "&a|"
  noprogress: "<gray>|"
  barlength: 16
```

---

## 🗂️ Hệ thống Group (đặt ngẫu nhiên theo nhóm)

Group cho phép gom nhiều block ID thành 1 nhóm, khi đặt sẽ random ra 1 ID trong nhóm đó — tiện cho việc tạo các node khoáng sản đa dạng (ví dụ: 70% Iron Node, 30% Gold Node) trong cùng một khu vực mỏ.

```
/mmoblock placegroup ore_cluster_1
```

> Cấu hình group nằm trong file riêng (không thuộc nhóm file đã gửi ở đây) — kiểm tra trong `plugins/MMOBlock/` để biết file group config cụ thể trên bản bạn đang dùng.

---

## 🏗️ Cấu trúc đa block (`structures/<id>.yml`)

Sau khi chọn vùng và lưu bằng `/mmoblock struct save <id>`, file sinh ra có dạng:

```yaml
info:
  world: world
  total_blocks: 12
blocks:
  b0:
    material: STONE
    x: 100
    y: 64
    z: 200
    mmo_id: my_iron_node   # chỉ có nếu vị trí đó đang là 1 MMOBlock đã đặt
  b1:
    material: COBBLESTONE
    x: 101
    y: 64
    z: 200
```

File này dùng để lưu/dựng lại các cụm cấu trúc lớn (ví dụ một hang động đầy node khoáng sản) một cách nhất quán.

---

## 🗑️ Gỡ block đã đặt

- **Cách 1:** OP + Shift + click trái vào block → tự động xóa hologram, hitbox, ArmorStand model và dữ liệu trong database.
- **Cách 2:** `/mmoblock list` → click vào item trong GUI để xóa từ xa, không cần đứng tại vị trí block.

> Người chơi thường (không OP hoặc không sneak) click trái vào MMOBlock sẽ **không phá được** — event bị huỷ tự động để bảo vệ block khỏi bị đào trái phép qua đường vanilla.

---

## ❓ Lưu ý kỹ thuật

- Plugin dùng entity `Interaction` (Paper 1.20+) làm hitbox riêng cho từng block — không phụ thuộc hitbox gốc của block Minecraft, nên `width`/`height`/`offset_x,y,z` trong config hoàn toàn tùy biến được.
- Hologram dùng `TextDisplay`/`ItemDisplay` (entity hiển thị mới của Paper), không phải ArmorStand cũ — mượt và dễ style hơn nhưng yêu cầu phiên bản server đủ mới.
- Dữ liệu vị trí, group, UUID entity liên kết được lưu trong SQLite (`cache.db`), tự động nâng cấp schema khi cần (ví dụ cột `respawn_at` được thêm tự động nếu thiếu).
- `/mmoblock saveitem` lưu item dạng Base64 vào database — dùng để gắn item thật (custom enchant, lore, NBT...) làm phần thưởng thay vì chỉ material đơn giản trong `drops`.

---

## 📜 Bản quyền

Tác giả: **Thiện Dev**
Còn nhiều chức năng chưa ghi
