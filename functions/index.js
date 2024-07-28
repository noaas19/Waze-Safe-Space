const functions = require('firebase-functions');
const fetch = require('node-fetch');
const admin = require('firebase-admin');

admin.initializeApp();
const db = admin.database();

exports.extractAlerts = functions.pubsub.schedule('* * * * *').onRun(async (context) => {
    const url = 'https://api.tzevaadom.co.il/alerts-history';
    let alerts = [];

    try {
        // Fetch the page content using fetch
        const res = await fetch(url);
        const data = await res.json();

        // Get the current time
        const currentTime = Date.now();
        const oneMinuteAgo = currentTime - 60000; // דקה אחת אחורה

        // Process the JSON data
        alerts = data.reduce((a, b) => [...a, ...b.alerts], []);

        // Filter alerts that happened in the last minute and include Be'er Sheva
        alerts = alerts.filter(alert => {
            const alertTime = alert.time * 1000;
            const isRecent = alertTime >= oneMinuteAgo && alertTime <= currentTime;
            const isBeerSheva = alert.cities.some(city => city.includes("באר שבע") || city.includes("Beer Sheva"));
            return isRecent && isBeerSheva;
        });

        console.log('Filtered Alerts:', alerts);

        // Save filtered alerts to Firebase Realtime Database if there are any
        if (alerts.length > 0) {
            const updates = {};
            alerts.forEach(alert => {
                const newKey = db.ref().child('alerts').push().key;
                updates['/alerts/' + newKey] = {
                    id: alert.id,
                    time: new Date(alert.time * 1000).toISOString(), // המרת זמן מה-epoch ל-ISO
                    cities: alert.cities.join(', '), // המרת רשימת ערים למחרוזת
                    threat: alert.threat

                };
            });
            await db.ref().update(updates);
        } else {
            console.log('No new alerts for Be\'er Sheva in the last minute.');
        }

        return null;
    } catch (error) {
        console.error('Error fetching data:', error);
        throw new functions.https.HttpsError('internal', 'Unable to fetch data.');
    }
});
