# 標籤譯名資料

譯名由 [EhTagTranslation/Database](https://github.com/EhTagTranslation/Database) 的貢獻者提供。簡體與臺灣繁體資料取自 [EhPanda-Team/ehpanda-tag-database v7.30121.1](https://github.com/EhPanda-Team/ehpanda-tag-database/releases/tag/v7.30121.1)；該資料集使用 OpenCC 產生臺灣繁體版本。

`tag_names_zh.tsv` 僅擷取譯名、移除圖片和連結標記，並依照 nhentai 的標籤類型重新編排；不包含百科說明、圖片或外部連結。不同命名空間的譯名不一致時，顯示原始標籤。

譯名資料依 [CC BY-NC-SA 3.0 中國大陸](https://creativecommons.org/licenses/by-nc-sa/3.0/cn/) 授權，完整授權文字隨附於 `tag_translations_license.md`。NVbook 程式碼仍依 Apache 2.0 授權。

更新時，從指定版本下載 `zh-Hans.json` 和 `zh-Hant.json`，並在專案根目錄執行：

```sh
python scripts/import_wiki_tag_names.py zh-Hans.json zh-Hant.json app/src/main/assets/tag_names_zh.tsv
```

同時更新上方的版本與來源連結。
