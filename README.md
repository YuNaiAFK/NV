# NVbook

NVbook 是一款開放原始碼的非官方 nhentai Android 閱讀應用程式，支援關鍵字搜尋、標籤篩選、離線閱讀、本機收藏與書籤。

## 專案來源

NVbook 以 [maxwai/NClientV3](https://github.com/maxwai/NClientV3) 的 [`8877d12` 提交](https://github.com/maxwai/NClientV3/commit/8877d1220b17ae3e3e669ef21c1106049027f0f5) 為基礎。NClientV3 則源自 [Dar9586/NClientV2](https://github.com/Dar9586/NClientV2)。感謝兩個上游專案的維護者與貢獻者，完整名單請見[上游致謝](https://github.com/maxwai/NClientV3/blob/8877d1220b17ae3e3e669ef21c1106049027f0f5/README.md#contributors)。

目前的程式碼存放庫：[YuNaiAFK/NV](https://github.com/YuNaiAFK/NV)。本專案與 nhentai 官方無隸屬關係。

## 目前功能

- 依關鍵字或標籤搜尋，也能排除標籤或隱藏相關內容。
- 下載後離線閱讀，並管理本機收藏、書籤與瀏覽紀錄。
- 支援 PIN 碼鎖定，也能將應用程式圖示偽裝成計算機。
- 沿用上游的 API 金鑰驗證方式；新增的官網登入流程與官網收藏同步尚未完成。

## 安裝與建置

若已有 APK 釋出，請至本專案的 [GitHub Releases](https://github.com/YuNaiAFK/NV/releases) 下載。Android 9 以上請使用一般版本；Android 8.0–8.1 請選擇檔名含 `pre28` 的版本。

從原始碼建置需要 JDK 17 和 Android SDK 36。Gradle 會讀取存放庫根目錄下的 `keystore.properties`。若只要建置偵錯版，可使用與[現有 CI](.github/workflows/android.yml) 相同的暫用設定，不需要提供正式版簽署金鑰：

```properties
storePassword=dummy
keyPassword=dummy
keyAlias=dummy
storeFile=dummy.jks
```

在存放庫根目錄執行 `./gradlew assemblePost28Debug`（Windows：`.\gradlew.bat assemblePost28Debug`）。建置完成後，可在 `app/build/outputs/apk/` 下找到 APK。若要建置 Android 8 的偵錯版，請改用 `assemblePre28Debug`。

## 授權條款

本專案沿用 [Apache License 2.0](LICENSE)。上游著作權聲明：Copyright 2024 maxwai。
