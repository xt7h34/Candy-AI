# Candy (com.candyai)

تطبيق كاندي AI. بيكلّم سيرفر `candy-server` (مش بوت كندا).

- عنوان السيرفر: سطر واحد في `api-url.txt`، وممكن يتغير من جوا التطبيق (الترس).
- كل push على `main` بيطلّع APK في Releases.
- التحديث من الموبايل: ارفع `update.zip` في جذر الريبو.
- مفتاح التوقيع: أسرار GitHub (`KEYSTORE_B64` و`KEYSTORE_PASSWORD` و`KEY_ALIAS` و`KEY_PASSWORD`). من غيرهم البناء يشتغل بمفتاح مؤقت.
