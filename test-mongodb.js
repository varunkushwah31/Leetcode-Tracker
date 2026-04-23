const { MongoClient } = require('mongodb');

// Pointing to local mongodb (use 'mongodb' if running inside docker, 'localhost' if running on host)
const uri = "mongodb://localhost:27017/LeetcodeTracker";

async function run() {
    const client = new MongoClient(uri);
    try {
        console.log("Attempting to connect to LOCAL MongoDB...");
        await client.connect();
        console.log("Successfully connected to Local MongoDB!");
        const database = client.db("LeetcodeTracker");
        console.log("Connected to database:", database.databaseName);
    } catch (err) {
        console.error("Failed to connect to Local MongoDB. Is the container running?");
        console.error(err);
    } finally {
        await client.close();
    }
}
run();
