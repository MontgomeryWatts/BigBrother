import org.json.JSONObject;

public class Entry {
    private JSONObject user;
    private StringBuilder builder = new StringBuilder();

    public Entry(JSONObject user){
        this.user = user;
    }

    public void appendWord(String word){
        this.builder.append(word);
    }

    public JSONObject getUser() {
        return user;
    }

    public StringBuilder getBuilder() {
        return builder;
    }
}
