# QA-4 · Pro purchase through the shared billing library

30 October 2026. Scope: the one-time Pro purchase of ADR-0022 (`one.tearoom:unlock`), tested on Mathis' phone with the production build 1.8.0 (code 15) installed from Google Play, a private Google account that is a licence tester, and Play's test payment methods.

## Preconditions

- The account is listed under Play Console → Settings → Licence testing. That is why the production build offers test payment methods and no money moves.

- The app is installed from Google Play, not sideloaded.

- A second Google account without the purchase is available on the phone.

- Ideally a second device is signed in with the first account.

## Already done

- Test purchase with the default test card succeeded and Pro unlocked (30 October 2026).

## Test cases

Pro sheet texts are quoted from `strings_app.xml` and `strings_editor.xml`. Open the Pro sheet from any Pro mark in the editor.

### 1. Pending payment

Steps:

- Make sure Pro is not owned (clear the app's data, or use the second account).

- Open the Pro sheet, tap "Buy for …", pick the test card "slow approval" and confirm.

- Look at the sheet, then try to buy again.

- Wait a few minutes until Play approves the payment, then close the app and open it again (Play is asked on every resume).

Expected:

- The sheet shows "The purchase is pending. Pro unlocks once it is paid."

- A second purchase is blocked while the payment is pending.

- After approval and reopening, Pro is unlocked ("Thank you! Pro is unlocked.") and the Pro marks are gone.

Result: 

### 2. Cancelled purchase

Steps:

- Without Pro, tap "Buy for …" and back out of the Play dialog (back button or tap outside).

Expected:

- No Pro, no crash, the sheet is usable again and "Buy for …" is enabled.

- The library ignores a cancel on purpose, so no message appears. "Google Play could not open the purchase. Please try again." only appears when Play cannot open the dialog at all.

Result: 

### 3. Declined payment

Steps:

- Without Pro, tap "Buy for …", pick the test card "always declines" and confirm.

Expected:

- Play shows its own decline notice; if the dialog closes with an error, the app shows "The purchase did not go through."

- No Pro.

Result: 

### 4. Restore on the same account

Steps:

- With the purchase on the account, clear the app's data (or uninstall and reinstall from Play).

- Open the Pro sheet and tap "Restore purchase".

Expected:

- Pro is active again and the sheet shows "Pro is active on this device."

Result: 

### 5. Restore on another account without the purchase

Steps:

- Switch the Play account to the second one (Play Store → profile picture).

- Open the Pro sheet in the app (data cleared, so no cached Pro) and tap "Restore purchase".

Expected:

- "Google Play found no Pro purchase for this Google account. If you bought Pro with another Google account, switch to it in the Play Store and tap “Restore purchase” again."

- No Pro.

Result: 

### 6. Offline

Steps:

- With Pro owned, switch on aeroplane mode and force-stop the app, then start it cold.

- Open the Pro sheet. (Without a cached Pro, "Restore purchase" offline shows "Google Play could not be reached, so nothing was checked. Please try again later.")

- Switch aeroplane mode off and tap "Try again".

Expected:

- Pro stays unlocked from the cache; an unreachable Play never removes it.

- The sheet explains the missing price: "Google Play cannot be reached right now, so there is no price yet. Check the connection and try again." with the button "Try again".

- Online, "Try again" shows the price.

Result: 

### 7. Second device, same account

Steps:

- On the second device, signed in with the account that owns Pro, install the app from Google Play and open it.

Expected:

- Pro is recognised on resume without pressing "Restore purchase".

Result: 

### 8. Pro sheet in German and Russian at 360 dp

Steps:

- Set the phone to a 360 dp wide layout (Settings → Display → display size, or use a narrow phone), then switch the app language to German and to Russian.

- Open the Pro sheet without Pro, also while a price problem or the pending text is shown if that is easy to reach.

Expected:

- No clipped or overlapping text, no horizontal scrolling, "Buy for …", "Try again" and "Restore purchase" are readable. A quick look is enough.

Result: 

### 9. Real purchase and refund (last, optional)

Test purchases do not appear in Play Console → Order management, so a real order is the only way to test the revocation path.

Steps:

- Remove the account from Settings → Licence testing and wait for it to propagate. If Play still offers the test card, clear the cache of the Play Store app.

- Clear the app's data, buy Pro for real and check that it unlocks.

- In Play Console → Order management, refund the order.

- Start the app again (a cold start, or wait for a resume).

- Add the account to the licence testers again.

Expected:

- After the refund and the next start, Pro is gone and the Pro marks are back. Play must have answered; offline the cache keeps Pro.

Result: 

## Open points

- The Play licence key for the signature check (`publicKeyBase64` in `Pro.kt`) is still `null`. Once it is added, the licence-test purchase (cases 1 to 4 at least) has to be repeated, since a wrong key would hide every purchase.
