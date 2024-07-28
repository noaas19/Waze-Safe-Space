/**
 * Import function triggers from their respective submodules:
 *
 * const {onCall} = require("firebase-functions/v2/https");
 * const {onDocumentWritten} = require("firebase-functions/v2/firestore");
 *
 * See a full list of supported triggers at https://firebase.google.com/docs/functions
 */
const functions = require('firebase-functions');
const axios = require('axios');
const cheerio = require('cheerio');
const admin = require('firebase-admin');

admin.initializeApp();
const db = admin.database();

exports.scrapeAlerts = functions.pubsub.schedule('* * * * *').onRun(async (context) => {
    const url = 'https://api.tzevaadom.co.il/alerts-history';
    const alerts = [];

    try {
        // Fetch the page content
        const response = await axios.get(url);
        const $ = cheerio.load(response.data);

        // Extract alerts data
        $('.alert_table').each((i, element) => {
            const alertType = $(element).find('h4.alertTableCategory').text().trim();

            $(element).find('.alertDetails').each((j, detailElement) => {
                const time = $(detailElement).find('h5.alertTableTime').text().trim();
                const location = $(detailElement).text().replace(time, '').trim();

                alerts.push({
                    type: alertType,
                    time: time,
                    location: location
                });
            });
        });

        console.log('Alerts:', alerts);

        // Save alerts to Firebase Realtime Database
        await db.ref('alerts').set({
            timestamp: new Date().toISOString(),
            alerts: alerts
        });

        return null;
    } catch (error) {
        console.error('Error fetching data:', error);
        throw new functions.https.HttpsError('internal', 'Unable to fetch data.');
    }
});



// Create and deploy your first functions
// https://firebase.google.com/docs/functions/get-started

// exports.helloWorld = onRequest((request, response) => {
//   logger.info("Hello logs!", {structuredData: true});
//   response.send("Hello from Firebase!");
// });
