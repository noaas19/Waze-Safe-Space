

const functions = require('firebase-functions');
const axios = require('axios');
const admin = require('firebase-admin');

admin.initializeApp();
const db = admin.database();


exports.scrapeAlerts = functions.pubsub.schedule('* * * * *').onRun(async (context) => {
    const url = 'https://api.tzevaadom.co.il/alerts-history';
    const alerts = [];
    const now = Date.now();
    const twentyFourHoursAgo = now - 24 * 60 * 60 * 1000;

    try {
        // Fetch the page content
        const response = await axios.get(url);
        const data = response.data;
        // Process the JSON data
        data.forEach(item => {
            item.alerts.forEach(alert => {
                const alertTime = alert.time * 1000;

                if (alertTime >= twentyFourHoursAgo && alert.cities.includes('קריית שמונה')) {
                    alerts.push({
                        id: item.id,
                        time: new Date(alertTime).toISOString(),
                        cities: alert.cities.join(', '),
                        threat: alert.threat,
                    });
                }
            });
        });

        // Add simulated alert for Be'er Sheva
        const simulatedAlert = {
            id: 'simulated-id',
            time: new Date(now).toISOString(),
            cities: 'באר שבע',
            threat: 'rocket',
        };
        alerts.push(simulatedAlert);

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

//exports.scrapeAlerts = functions.pubsub.schedule('* * * * *').onRun(async (context) => {
//    const url = 'https://api.tzevaadom.co.il/alerts-history';
//    const alerts = [];
//    const now = Date.now();
//    const twentyFourHoursAgo = now - 24 * 60 * 60 * 1000;
//
//    try {
//        // Fetch the page content
//        const response = await axios.get(url);
//        const data = response.data;
//        // Process the JSON data
//        data.forEach(item => {
//            item.alerts.forEach(alert => {
//                const alertTime = alert.time * 1000;
//
//
//                if (alertTime >= twentyFourHoursAgo && alert.cities.includes('קריית שמונה')) {
//                    alerts.push({
//                        id: item.id,
//                        time: new Date(alertTime).toISOString(),
//                        cities: alert.cities.join(', '),
//                        threat: alert.threat,
//
//                    });
//                }
//            });
//        });
//
//        console.log('Alerts:', alerts);
//
//        // Save alerts to Firebase Realtime Database
//        await db.ref('alerts').set({
//            timestamp: new Date().toISOString(),
//            alerts: alerts
//        });
//
//        return null;
//    } catch (error) {
//        console.error('Error fetching data:', error);
//        throw new functions.https.HttpsError('internal', 'Unable to fetch data.');
//    }
//});