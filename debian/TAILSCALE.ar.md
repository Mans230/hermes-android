# اتصال شخصي بدون شراء دومين

المسار المقترح: Tailscale على Debian وأندرويد، ثم Tailscale Serve لإنشاء عنوان HTTPS خاص بالشبكة. لا تحتاج شراء دومين؛ العنوان يكون مثل `birella.<tailnet>.ts.net`. يجب أن يكون الموبايل متصلًا بشبكة Tailscale المسموح لها بالوصول للسيرفر.

## قبل تغيير السيرفر

النسخة التي أرسلتها: Hermes Agent **v0.21.0**، بتاريخ 2026-08-31، upstream `9dd6634c`، داخل `/usr/local/lib/hermes-agent`، Python 3.11.16. هذه معلومات من ناتجك وليست نتيجة فحص عن بعد.

لم يمكن التحقق من ملف API في ذلك الـcommit عبر المصدر العام. لذلك لا نعتبر رقم النسخة دليلًا على تفعيل API أو دعم كل المسارات. فحص `check-birella.py` يبحث فقط في ملفات كود API ويتحقق من health المحلي دون قراءة مفاتيح أو إعدادات أو إعادة تشغيل خدمات.

```bash
python3 debian/check-birella.py
hermes profile list
```

لو كانت البوتات تعمل تحت مستخدم آخر، شغّل قائمة Profiles تحت ذلك المستخدم. حساب root الذي أرسلت منه الإصدار لا يثبت هوية مستخدم خدمة gateway.

## بعد تأكيد أن API شغّال محليًا

1. ثبّت Tailscale من دليل Linux الرسمي على Debian ومن تطبيق Android الرسمي على الهاتف، ثم أدخلهما في نفس الشبكة الخاصة. لم يتم تنفيذ أي تثبيت هنا.
2. راجع وضع Serve الحالي أولًا حتى لا تستبدل خدمة موجودة:

```bash
tailscale serve status
```

3. إذا لم يكن المسار/المنفذ مستخدمًا، يمكن تقديم API المحلي داخل شبكتك:

```bash
tailscale serve --bg http://127.0.0.1:8642
```

سيعرض Tailscale رابط HTTPS وقد يطلب تفعيل شهادات HTTPS في الشبكة. ضع الرابط الناتج نفسه في التطبيق. المثال ذو `<tailnet>` ليس عنوان اتصال فعليًا.

4. اترك مصادقة Hermes API مفعّلة، واستخدم مفتاح API في التطبيق. Tailscale لا يغني عن مفتاح Hermes.

لا تستخدم Funnel لهذا المسار الشخصي؛ Serve كافٍ للوصول داخل شبكتك. لو احتجت لاحقًا الوصول بدون تشغيل Tailscale على الموبايل، نستخدم دومين عام مع HTTPS ومصادقة API.

## أكثر من Profile

لا نفترض أن كل بوت Telegram يملك API منفصلًا. نتيجة `hermes profile list` وطريقة تشغيل الخدمات تحددان الربط. كل اتصال في التطبيق يجب أن يشير إلى الـProfile المقصود فعلًا. تعريف أسماء وشخصيات متعددة لنفس URL والمفتاح لا ينشئ Profiles معزولة على السيرفر.

المراجع:
- https://tailscale.com/docs/features/tailscale-serve
- https://tailscale.com/docs/reference/tailscale-cli/serve
- https://tailscale.com/download/linux
- https://tailscale.com/download/android
- https://hermes-agent.nousresearch.com/docs/user-guide/profiles/
