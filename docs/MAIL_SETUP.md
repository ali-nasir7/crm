# Mail Setup — Aasaan aur Basic Guide (Mail Setup Made Simple)

Ye guide aapko CRM me mail chalu karne ka poora tareeqa deti hai. Koi technical jargon
nahi — bas steps follow karo aur mail chal padega.

---

## 1. Sabse pehle ye samjho (2 minute)

CRM mail aise bhejta hai:

```
CRM  ->  SMTP server (aapka Gmail ya koi aur)  ->  recipient ka inbox
```

CRM khud seedha mail NAHI bhejta — wo aapke SMTP server se bolta hai "ye mail bhej do".
Is liye **CRM me ek SMTP account connect hona zaroori hai.** Jab tak ek bhi VERIFIED
sender account nahi hoga, tab tak:

- Naye user ko credential mail NAHI jayega (account banega, admin ko temp password dikhega)
- Reminder mail NAHI jayega (task OPEN rehta hai, mail agle sweep pe try karta hai)
- Lead se customer ko mail NAHI jayega (composer kehta hai "No sender account configured")

**Ye bug nahi hai — ye "mail tanki khali hai" wali halat hai.** Neeche ke steps se tanki bharo.

## 2. Gmail se mail chalu karo (sabse aasan tareeqa, 5 minute)

### Step 1 — Google account me 2-Step Verification on karo
1. Browser me jao: <https://myaccount.google.com/security>
2. "2-Step Verification" dhoondo → **Turn on** karo (aapka phone number se verify hoga).

### Step 2 — App Password banao (ye 16-letter password CRM me lagega)
1. Jao: <https://myaccount.google.com/apppasswords>
2. App name likho, misal: `CRM` → **Create**.
3. Google ek **16-letter password** dega, misal: `abcd efgh ijkl mnop`
4. Isay kahin mehfooz likh lo. **Ye aapka Gmail ka asli password nahi hai** — ye sirf
   CRM jaise apps ke liye hai.

### Step 3 — CRM me account connect karo
1. CRM kholo → **Emails** menu → **Accounts** tab.
2. **Connect account** pe click karo.
3. Ye values bharo (bilkul aise hi):

   | Field | Value |
   |---|---|
   | Provider | **SMTP** |
   | Email | `aapkaemail@gmail.com` |
   | Display name | `Ali Nasir` (jo naam sender pe dikhe) |
   | Reply-To | khaali chhod do (optional) |
   | SMTP Host | `smtp.gmail.com` |
   | SMTP Port | `587` |
   | Encryption | `STARTTLS` |
   | SMTP Username | `aapkaemail@gmail.com` |
   | SMTP Password | wo **16-letter App Password** (spaces ke saath ya bina — dono chalega) |
   | Daily limit | `500` (Gmail free limit ke andar raho) |

4. **Save** karo → phir list me us account pe **Shield/tick (Verify)** button dabao.
   - `VERIFIED` green dikhna chahiye = CRM ne aapke Gmail me asli login test kiya, kaamyab.

### Step 4 — Test mail bhejo
- Accounts list me **Send test** button dabao → apna email likho → **Send**.
- 30 second me inbox me mail aani chahiye (spam bhi check karna).

**Bas. Ab CRM ka POORA mail system chalta hai:**
- Naya user banao → usay credential email jayegi
- HOT lead ka reminder → owner ko email jayegi
- Lead page se customer ko email → jayegi aur track hogi
- Campaign → recipients ko jayegi

## 3. Local testing bina real mail ke (Mailpit — developers ke liye)

Agar aap test karna chahte ho ke CRM mail bhej raha hai **bina kisi real insaan ko
mail bheje**, to Mailpit use karo — ye ek nakli (fake) inbox hai jo aapke PC pe chalti hai:

```powershell
docker compose up -d mailpit
```

Phir CRM ke **Emails > Accounts** me ye values daalo:

| Field | Value |
|---|---|
| SMTP Host | `localhost` |
| SMTP Port | `1025` |
| Encryption | `NONE` |
| Username / Password | khaali chhod do |
| Email | `test@local.test` |

Ab CRM me jo bhi mail bhejoge wo <http://localhost:8025> pe dikh jayega — bina
kisi ko asli mail bheje. Testing ke liye best hai.

## 4. Har feature kaunsa sender use karta hai?

| Feature | Pehle kahan dekhta hai | Phir |
|---|---|---|
| Naya user credential mail | Emails > Accounts wala **VERIFIED** account | Env vars (`CRM_MAIL_*`) agar UI account na ho |
| Lead reminder (3-day) | Sirf **VERIFIED** SMTP account | Koi nahi — account ke bina task OPEN rehta hai aur har 10 min me dobara koshish |
| Lead page se customer email | Jo account aap composer me chunte ho | VERIFIED preferred |
| Campaign | Campaign ke sender picker me chuna hua account | — |

## 5. Masla ho to ye table dekho

| Symptom (kya ho raha hai) | Wajah (kyun) | Hal (kya karo) |
|---|---|---|
| User banaya, mail nahi gaya, toast me "Email failed - temp password" | Koi VERIFIED account nahi tha aur env vars bhi nahi | Step 2-3 karo (Gmail app password + Connect account) |
| "Email failed - temp password" toast ke bawajood VERIFIED account hai | SMTP login fail hua (ghalat password / 2FA app password nahi use kiya) | App Password dobara banao; normal Gmail password KABHI na daalo |
| Verify button red/hot profile hai "authentication failed" | 16-letter app password me ghalti | Dobara copy-paste karo; spaces hata ke bhi try karo |
| Lead se mail bhejne pe "Select a sender email account first" | Koi account connect nahi | Step 3 karo |
| Lead se mail gaya par customer ko nahi aaya | Spam me gaya ya recipient ne block kiya | Spam folder check karo; Gmail ke liye SPF/DKIM doc dekho (`docs/EMAIL_DELIVERABILITY.md`) |
| Reminder ka task OPEN para hai, mail nahi ja raha | VERIFIED account nahi tha jab sweep chala | Account verify karo — agle sweep (10 min) me mail chala jayega |
| "Email could not be sent: Could not connect to SMTP host" | Port/encryption ghalat, ya company firewall ne 587 block kiya | Port `587` + `STARTTLS` try karo; phir `465` + `SSL`; Wi-Fi badal ke test karo |
| Gmail bolta hai "credentials not accepted" | Aapne App Password ki jagah normal password dala | Step 2 dobara — App Password hi lagega |
| Mail chali gayi par inbox me nahi aayi (bina error ke) | Recipient ka server ne silently discard kiya (spam rules) | Ye server-side hota hai — SPF/DKIM/DMARC setup karo, warm-up karo (roz thoda thoda bhejo) |

## 6. Env-var fallback (UI ke bagair, sirf advanced users ke liye)

Agar aap UI account connect nahi karna chahte, to `.env` me:

```env
CRM_MAIL_HOST=smtp.gmail.com
CRM_MAIL_PORT=587
CRM_MAIL_USERNAME=aapkaemail@gmail.com
CRM_MAIL_PASSWORD=xxxxxxxxxxxxxxxx
CRM_MAIL_FROM=aapkaemail@gmail.com
```

Par **UI account recommended hai** — usme verify + test send + reply-to sab built-in hai.
UI account hamesha env fallback ko prefer kiya jata hai.

## 7. Spam me na jaye — bas itna

- Gmail se bhej rahe ho to Gmail khud SPF/DKIM laga deta hai — aapko kuch nahi karna.
- Apna domain (misal `@alicrm.com`) se bhejna hai to pehle DNS me SPF + DKIM + DMARC
  lagao — poori details: `docs/EMAIL_DELIVERABILITY.md`
- Shuru me roz 20-50 mail se shuru karo, phir dheere dheere barhao (naye domain ke saath
  pehle hi din 5000 mail = spam folder).

---

**Ek line me:** Emails > Accounts > Connect (Gmail app password ke saath) > Verify >
Send test. Itne me CRM ka mail system poori tarah chal jata hai.
