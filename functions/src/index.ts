//
// File: functions/src/index.ts (atau index.js)
//
import * as functions from "firebase-functions";
import * as admin from "firebase-admin";

admin.initializeApp();
const db = admin.firestore();

/**
 * Cloud Function (Trigger)
 * Otomatis membuat kode referral unik ketika user di-update menjadi premium.
 */
export const generateReferralCode = functions.firestore
  .document("users/{userId}")
  .onUpdate(async (change, context) => {
    const beforeData = change.before.data();
    const afterData = change.after.data();

    // Cek apakah status 'isPremium' baru saja berubah dari false menjadi true
    if (beforeData.isPremium === false && afterData.isPremium === true) {
      const userId = context.params.userId;
      const userRef = db.collection("users").doc(userId);
      console.log(`User ${userId} just became premium. Generating referral code.`);

      let uniqueCode: string;
      let isUnique = false;

      // Loop untuk memastikan kode yang dihasilkan benar-benar unik
      do {
        // Hasilkan kode acak 6 karakter (a-z, A-Z, 0-9)
        const chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        uniqueCode = "";
        for (let i = 0; i < 6; i++) {
          uniqueCode += chars.charAt(Math.floor(Math.random() * chars.length));
        }

        // Cek keunikan di koleksi 'referralCodes'
        const codeRef = db.collection("referralCodes").doc(uniqueCode);
        const doc = await codeRef.get();
        if (!doc.exists) {
          isUnique = true;
        }
      } while (!isUnique);

      console.log(`Generated unique code ${uniqueCode} for user ${userId}.`);

      // Simpan kode unik ke koleksi 'referralCodes' dan ke dokumen user
      const referralCodeRef = db.collection("referralCodes").doc(uniqueCode);

      await db.runTransaction(async (transaction) => {
        // 1. Simpan mapping kode -> userId
        transaction.set(referralCodeRef, { userId: userId });
        // 2. Simpan kode di profil user
        transaction.update(userRef, { referralCode: uniqueCode });
      });

      console.log("Referral code successfully saved.");
      return null;
    }

    return null;
  });


/**
 * Cloud Function (Callable)
 * Dipanggil dari aplikasi saat user baru mendaftar dengan kode referral.
 */
export const processReferralOnRegister = functions.https.onCall(
  async (data, context) => {
    const referralCode = data.referralCode;
    const newUserId = context.auth?.uid; // ID user yang baru daftar

    // Validasi input
    if (!newUserId) {
      throw new functions.https.HttpsError(
        "unauthenticated",
        "User must be authenticated to call this function."
      );
    }
    if (!referralCode || typeof referralCode !== "string") {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "Referral code must be a non-empty string."
      );
    }

    const referralCodeRef = db.collection("referralCodes").doc(referralCode);
    const codeDoc = await referralCodeRef.get();

    // Cek apakah kode referral valid
    if (!codeDoc.exists) {
      throw new functions.https.HttpsError(
        "not-found",
        "Referral code is not valid."
      );
    }

    const referrerId = codeDoc.data()?.userId;

    // User tidak bisa mereferensikan diri sendiri
    if (referrerId === newUserId) {
       throw new functions.https.HttpsError(
        "invalid-argument",
        "You cannot use your own referral code."
      );
    }

    console.log(`Processing referral. New user: ${newUserId}, Referrer: ${referrerId}`);

    const referrerRef = db.collection("users").doc(referrerId);
    const transactionRef = db.collection("transactions").doc(); // Dokumen mutasi baru

    const rewardAmount = 10000;

    // Gunakan transaction untuk memastikan semua operasi berhasil atau gagal bersamaan
    await db.runTransaction(async (transaction) => {
      const referrerDoc = await transaction.get(referrerRef);
      if (!referrerDoc.exists) {
         throw new functions.https.HttpsError("not-found", "Referrer not found.");
      }

      const currentBalance = referrerDoc.data()?.balance || 0;
      const newBalance = currentBalance + rewardAmount;

      // 1. Update saldo referrer
      transaction.update(referrerRef, { balance: newBalance });

      // 2. Buat catatan di mutasi/transactions untuk referrer
      transaction.set(transactionRef, {
        userId: referrerId,
        type: "REFERRAL_BONUS",
        amount: rewardAmount,
        description: `Bonus referral dari user baru`,
        timestamp: admin.firestore.FieldValue.serverTimestamp(),
      });
    });

    console.log(`Successfully rewarded ${referrerId} with ${rewardAmount}.`);
    return { success: true, message: "Referral processed successfully!" };
  }
);