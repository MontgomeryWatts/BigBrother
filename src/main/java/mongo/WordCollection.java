package mongo;

import com.mongodb.MongoClient;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import entities.Entry;
import net.dv8tion.jda.core.entities.User;
import org.bson.Document;
import org.json.JSONObject;

import java.util.*;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

public class WordCollection {
    private MongoCollection<Document> wordCollection;
    private MongoCollection<Document> userCollection;

    public WordCollection(){
        MongoClient client = new MongoClient();
        MongoDatabase db = client.getDatabase("test");
        wordCollection = db.getCollection("words");
        userCollection = db.getCollection("users");
    }

    /**
     * Adds a User to the database
     * @param user The User to add
     */

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

    /**
     * Adds all passed words to the database. Checks to see if there is a document representing the word first,
     * and if not it creates one. Then checks a nested document containing information pertaining to what servers
     * and which people are monitoring that word, and attempts to add the server ID and user ID to the watchlist.
     * @param serverID String representing the server's ID
     * @param user The User who would like to be notified when these words are said
     * @param sentence String containing the word(s) to add to the database.
     */

    @SuppressWarnings("unchecked")
    public void addWords(String serverID, User user, String sentence){

        String userId = user.getId();
        if( findDocByUserId(userId) == null)
            addUser(user);

        for (String word: formatString(sentence) ) {
            Document doc = findDocByWord(word);

            //Create document for word being added and insert it if it didn't already exist
            if (doc == null){
                doc = new Document("_id", word)
                        .append("servers", new ArrayList<Document>());
                wordCollection.insertOne(doc);
            }

            HashMap<String, Document> map = getServerMap(doc);

            //If there is a nested document for the server
            if (map.containsKey(serverID)){
                Document serverDoc = map.get(serverID);
                ArrayList<String> users = (ArrayList<String>) serverDoc.get("users");

                //User is already monitoring this word, attempt to add next word
                if (users.contains(userId))
                    continue;

                users.add(userId);
                wordCollection.updateOne(eq("_id", word), pull("servers", serverDoc));
                wordCollection.updateOne(eq("_id", word), addToSet("servers",
                        new Document("_id", serverID).append("users", users)));
            }

            else {
                wordCollection.updateOne(doc, addToSet("servers", new Document("_id", serverID).append("users", Collections.singletonList(userId))));
            }
        }
    }

    /**
     * Checks all the words in the passed sentence to see if they contain monitored words. A word can be determined to
     * be monitored if it has a corresponding document. If a document is found, check its servers array for nested
     * documents.If the server the message originated from is in the array, add any users to the HashMap so they
     * may be notified.
     * @param serverID The ID of the server the message originated from.
     * @param sentence String representing the message.
     * @return A HashMap that can be used to alert any users of monitored words, or null.
     */

    @SuppressWarnings("unchecked")
    public HashMap<String, Entry> checkWords(String serverID, String sentence){
        HashMap<String, Entry> results = new HashMap<>();
        for (String word: formatString(sentence)){
            Document doc = findDocByWord(word);

            //Nobody is monitoring that word, check rest of sentence
            if(doc == null)
                continue;

            HashMap<String, Document> servers = getServerMap(doc);

            //If there is a server that has someone monitoring that word
            if (servers.containsKey(serverID)){
                Document serverDoc = servers.get(serverID);

                //Create an entry for each user
                for (String userId: (ArrayList<String>) serverDoc.get("users") ){
                    if (!results.containsKey(userId)){
                        Document userDoc = findDocByUserId(userId);
                        JSONObject user = getJSONFromUserDoc(userDoc);
                        Entry entry = new Entry(user);
                        entry.appendWord(word);
                        results.put(userId, entry);
                    }
                    else{
                        Entry entry = results.get(userId);
                        entry.appendWord(" ");
                        entry.appendWord(word);
                        results.put(userId, entry);
                    }
                }
            }

        }
        return results.isEmpty() ? null : results;
    }

    /**
     * Returns a Document from the words collection corresponding to a passed String.
     * @param word A String representing the word to search for.
     * @return The corresponding Document or null.
     */

    private Document findDocByWord(String word){
        return wordCollection.find( eq("_id", word)).first();
    }

    /**
     * Returns a Document from the users collection corresponding to a passed String.
     * @param userId A String representing the ID of the user to search for.
     * @return The corresponding Document or null.
     */

    private Document findDocByUserId(String userId){
        return userCollection.find( eq("_id", userId)).first();
    }

    /**
     * Removes punctuation from a given string and splits it so it may be processed by checkWords
     * @param string The String to format
     * @return A String[] that can be iterated through by checkWords
     */

    private static String[] formatString(String string){
        return string.replaceAll("[^a-zA-Z0-9' ]", "").toLowerCase().split(" ");
    }

    /**
     * Creates a JSONObject from a Document in the users collection. Used by JDA to recreate a User Object.
     * @param doc The Document containing the user's information.
     * @return A JSONObject that can be used to recreate the User.
     */
    private JSONObject getJSONFromUserDoc(Document doc){
        return new JSONObject()
                .put("id", doc.getString("_id"))
                .put("username", doc.getString("name"))
                .put("discriminator", doc.getString("disc"));
    }

    /**
     * Returns a List of the Documents in a word Document's server array
     * @param doc A Document from the words collection to retrieve the nested Documents of.
     * @return A List of the nested server Documents
     */

    @SuppressWarnings("unchecked")
    private static List<Document> getServerDocs(Document doc){
        return (List<Document>) doc.get("servers");
    }

    /**
     * Uses getServerDocs to map the ID of the server Documents to the Documents themselves. Useful when determining
     * if a word Document already contains a nested Document for a specified server.
     * @param doc A Document from the words collection to retrieve and map the nested Documents of.
     * @return A HashMap mapping the Documents' IDs to the Documents themselves.
     */

    private static HashMap<String, Document> getServerMap(Document doc){
        HashMap<String, Document> map = new HashMap<>();
        for(Document d: getServerDocs(doc)) {
            map.put(d.getString("_id"), d);
        }

        return map;
    }


    /**
     * Gets all the words monitored by a user and returns them as a String.
     * @param serverID The ID of the server the get command was said in.
     * @param userID The ID of the user who requested their watched words.
     * @return A String containing the words being watched by the user.
     */

    public String getMonitoredWords(String serverID, String userID){
        StringBuilder builder = new StringBuilder();

        Document filter = new Document("servers._id", serverID)
                .append("servers.users", userID);

        for(Document doc: wordCollection.find( filter ) ) {
            String word = doc.getString("_id");
            builder.append(word)
                    .append(" ");
        }

        return builder.toString();
    }

    /**
     * Attempts to remove the user from the watchlist of all the words in the passed sentence.
     * @param serverID The ID of the server the remove command was issued on.
     * @param userId The ID of the user who issued the remove command.
     * @param sentence The sentence containing words to attempt to remove.
     * @return A String containing any words that were successfully removed.
     */

    @SuppressWarnings("unchecked")
    public String removeWords(String serverID, String userId, String sentence){

        StringBuilder builder = new StringBuilder();

        for(String word: formatString(sentence) ){
            Document doc = findDocByWord(word);

            //Word is not being monitored, check rest of sentence
            if (doc == null)
                continue;

            builder.append(word).append(" ");

            HashMap<String, Document> servers = getServerMap(doc);
            if (servers.containsKey(serverID)){
                Document serverDoc = servers.get(serverID);
                ArrayList<String> users = (ArrayList<String>) serverDoc.get("users");

                if (users.contains(userId)){

                    //There is more than one user monitoring the current word, so only remove the person who requested
                    //the word to be removed from their watchlist
                    if (users.size() > 1){
                        users.remove(userId);
                        wordCollection.updateOne(eq("_id", word), pull("servers", serverDoc));
                        wordCollection.updateOne(eq("_id", word), addToSet("servers",
                                new Document("_id", serverID).append("users", users)));
                    }

                    //There is only one user monitoring the current word
                    else {

                        //Only one server is monitoring this word, and on that one server, only one person.
                        //Document for that word can be deleted safely.
                        if (servers.size() == 1){
                            wordCollection.deleteOne(doc);
                        }

                        //There are other servers monitoring this word, but this server only has one person.
                        //Remove this server from the word document's server array
                        else if (servers.size() > 1 ){
                            wordCollection.updateOne(eq("_id", word), pull("servers", serverDoc));
                        }
                    }
                }
            }
        }

        return builder.toString();
    }
}
