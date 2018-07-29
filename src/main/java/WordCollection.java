import com.mongodb.MongoClient;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Updates;
import net.dv8tion.jda.core.entities.User;
import org.bson.Document;
import org.bson.codecs.DocumentCodec;
import org.bson.codecs.EncoderContext;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriter;
import org.bson.json.JsonWriterSettings;
import org.json.JSONObject;

import java.io.StringWriter;
import java.util.*;

import static com.mongodb.client.model.Filters.*;

public class WordCollection {
    private MongoCollection<Document> serverCollection;
    private MongoCollection<Document> userCollection;

    public WordCollection(){
        MongoClient client = new MongoClient();
        MongoDatabase db = client.getDatabase("test");
        serverCollection = db.getCollection("servers");
        userCollection = db.getCollection("users");
    }

    private void addUser(User user){
        Document doc = new Document("_id", user.getId())
                .append("name", user.getName())
                .append("disc", user.getDiscriminator());
        try {
            userCollection.insertOne(doc);
        } catch (MongoWriteException mwe){
            mwe.printStackTrace();
        }
    }

    public void addWords(String serverID, User user, String... words){

        //Gets document representing all words on this server to look for
        Document doc = findByServerId(serverID);
        if(doc == null) {
            doc = new Document("_id", serverID)
                    .append("words", new ArrayList<Document>());
            serverCollection.insertOne(doc);
        }

        //Adds user to database if they aren't already in it
        String userID = user.getId();
        if(userCollection.find( eq("_id", userID)).first() == null)
            addUser(user);

        HashMap<String, Document> map = getWordMap(doc);
        for(String word: words) {
            //If the word already has someone monitoring it
            if (map.containsKey(word)) {

                //If the current user already has the word added to their watchlist, do nothing
                Document nested = map.get(word);
                ArrayList<String> users = (ArrayList<String>) nested.get("users");
                if (users.contains(userID))
                    return;

                //Update the document if the current user is not on the word's watchlist
                users.add(userID);
                serverCollection.updateOne(doc, Updates.pull("words", nested));
                serverCollection.updateOne(doc, Updates.addToSet("words",
                        new Document("_id", word).append("users", users)));
            } else {
                serverCollection.updateOne(doc, Updates.addToSet("words", new Document("_id", word).append("users", Collections.singletonList(userID))));
            }

            //The doc has been modified, so updateOne would not match any documents without this.
            doc = serverCollection.find(new Document("_id", doc.getString("_id"))).first();
        }

    }

    public HashMap<String, Entry> checkWords(String serverID, String sentence){

        //replace with searching for the document for the current channel
        Document doc = findByServerId(serverID);
        if (doc == null) {
            return null;
        }

        HashMap<String, Document> map = getWordMap(doc);
        HashMap<String, Entry> results = new HashMap<>();

        for (String word: formatString(sentence) ) {
            if (map.containsKey(word)){
                Document wordDoc = map.get(word);
                for (String userID: (ArrayList<String>)wordDoc.get("users")){

                    //Adds entry for a user so they can be messaged
                    if(!results.containsKey(userID)){
                        Document userDoc = userCollection.find(eq("_id", userID)).first();
                        JSONObject user = getJSONFromUserDoc(userDoc);
                        Entry entry = new Entry(user);
                        entry.appendWord(word);
                        results.put(userID, entry);
                    } else {
                        Entry entry = results.get(userID);
                        entry.appendWord(" ");
                        entry.appendWord(word);
                        results.put(userID, entry);
                    }
                }
            }
        }

        return results.isEmpty() ? null : results;

    }

    private Document findByServerId(String serverID){
        return serverCollection.find( eq("_id", serverID)).first();
    }

    private static String[] formatString(String string){
        return string.replaceAll("[^a-zA-Z0-9' ]", "").toLowerCase().split(" ");
    }

    private JSONObject getJSONFromUserDoc(Document doc){
        return new JSONObject()
                .put("id", doc.getString("_id"))
                .put("username", doc.getString("name"))
                .put("discriminator", doc.getString("disc"));
    }

    public String getMonitoredWords(String serverID, String userID){
        StringBuilder builder = new StringBuilder();

        Document main = serverCollection.find( eq("_id", serverID)).first();
        if (main == null)
            return null;

        for (Document doc: getWordDocs(main)){
            ArrayList<String> users = (ArrayList<String>)doc.get("users");
            if (users.contains(userID)) {
                builder.append(doc.getString("_id"));
                builder.append(" ");
            }
        }

        return builder.toString();
    }

    @SuppressWarnings("unchecked")
    private static List<Document> getWordDocs(Document doc){
        return (List<Document>) doc.get("words");
    }

    /**
     * Returns a HashMap that maps the word representing the document's _id to the document itself
     * @param doc The document that contains the words array
     * @return A HashMap mapping the word to its corresponding document. e.g. frick would map to a Document
     * like { "_id" : "frick", "users" : [ "12345" ] }
     */

    private static HashMap<String, Document> getWordMap(Document doc){
        HashMap<String, Document> map = new HashMap<>();
        for(Document d: getWordDocs(doc)) {
            map.put(d.getString("_id"), d);
        }

        return map;
    }


}
