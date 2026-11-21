# NVbook

NVbook 是一款開放原始碼的非官方 nhentai Android 閱讀應用程式，支援關鍵字搜尋、標籤篩選、離線閱讀、本機收藏與書籤。

## 專案來源

NVbook 以 [maxwai/NClientV3](https://github.com/maxwai/NClientV3) 的 [`8877d12` 提交](https://github.com/maxwai/NClientV3/commit/8877d1220b17ae3e3e669ef21c1106049027f0f5) 為基礎。NClientV3 則源自 [Dar9586/NClientV2](https://github.com/Dar9586/NClientV2)。感謝兩個上游專案的維護者與貢獻者，完整名單請見[上游致謝](https://github.com/maxwai/NClientV3/blob/8877d1220b17ae3e3e669ef21c1106049027f0f5/README.md#contributors)。

目前的程式碼存放庫：[YuNaiAFK/NV](https://github.com/YuNaiAFK/NV)。本專案與 nhentai 官方無隸屬關係。

## 目前功能

- 依關鍵字或標籤搜尋，也能排除標籤或隱藏相關內容。
- 下載後離線閱讀，並管理本機收藏、書籤與瀏覽紀錄。
- 支援 PIN 碼鎖定，也能將應用程式圖示偽裝成計算機。
- 在「設定 → API key」點選「登入 nhentai 官網」，即可在瀏覽器登入；取得 API 金鑰後，回到 App 貼上並驗證。App 不接收官網密碼。
- 在「本機收藏」的 ⋮ 選單選擇「同步官網收藏」，即可補齊官網與本機各自缺少的收藏。同步不傳遞刪除動作，也不會建立重複收藏；如果中途停止，可再次執行同步。

作品詳情中的常見標籤有簡體及繁體中文顯示名稱。點選標籤可查看相同標籤的作品、喜歡標籤或在首頁隱藏相關作品。喜歡標籤不會影響搜尋結果；按住標籤可複製網站原名。

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

## Flutter 介面

新版瀏覽介面參考 [jiangtian616/JHenTai](https://github.com/jiangtian616/JHenTai) 的分頁導覽與封面瀏覽設計，以 Flutter 自行實作；未使用 JHenTai 的 E-Hentai 資料層、品牌或圖像。Android 宿主仍使用 NVbook 原有的 nhentai API、資料庫、帳號、收藏同步、下載與閱讀器。

新版介面已用 Flutter 3.50.1 驗證。啟用 Flutter 介面時，從存放庫根目錄執行：

```sh
cd flutter_home
flutter pub get
flutter analyze
flutter test
flutter build aar --no-profile
cd ..
./gradlew -PnvbookFlutter=true assemblePost28Debug
```

Windows 上請將最後一行改為 `.\gradlew.bat -PnvbookFlutter=true assemblePost28Debug`。Android 8 偵錯版本使用 `assemblePre28Debug`。不加 `-PnvbookFlutter=true` 則保留原本的 Android 介面。

正式版使用同一份 AAR 和專案原本的 `keystore.properties` 簽署設定，改執行 `assemblePost28Release` 或 `assemblePre28Release`。請勿提交簽署設定或金鑰。

## 發布流程

發行版在本機完成建置並驗證簽章後，使用 GitHub CLI 將兩個 APK 直接上傳至 [YuNaiAFK/NV Releases](https://github.com/YuNaiAFK/NV/releases)。發布標籤指向實際建置的開發分支提交，不直接推送 `main`，也不使用瀏覽器或 GitHub Actions 打包上傳。單純執行測試或驗證用的偵錯建置不建立 Release。

## 授權條款

本專案沿用 [Apache License 2.0](LICENSE)。上游著作權聲明：Copyright 2024 maxwai。
