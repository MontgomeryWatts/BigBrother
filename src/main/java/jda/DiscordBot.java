package jda;

import entities.Entry;
import mongo.WordCollection;
import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

import java.awt.*;
import java.time.OffsetDateTime;
import java.util.HashMap;

public class DiscordBot extends ListenerAdapter {
    private final String ADD_COMMAND = "!add";
    private final String GET_COMMAND = "!get";
    private final String DEL_COMMAND = "!delete";
    private final WordCollection wordCollection = new WordCollection();

    public static void main(String[] args) {
        try {
            JDA bot = new JDABuilder(AccountType.BOT)
                    .setToken(args[0])
                    .addEventListener(new DiscordBot())
                    .buildBlocking();
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        User author = event.getAuthor();
        if(!author.isBot()) {

            //Only do something if not privately messaged
            MessageChannel channel = event.getChannel();
            if(!channel.getType().equals(ChannelType.PRIVATE)) {

                Member member = event.getMember();
                Message message = event.getMessage();
                String content = message.getContentDisplay();
                String serverID = message.getGuild().getId();

                //Add words to alert for.
                if (content.startsWith(ADD_COMMAND)) {
                    String words = retrieveArgs(ADD_COMMAND, content);
                    wordCollection.addWords(serverID, author, words);
                    channel.sendMessage("Now monitoring this chat for word(s): " + words).queue();
                }

                else if (content.startsWith(DEL_COMMAND)){
                    String words = retrieveArgs(DEL_COMMAND, content);
                    String deleted = wordCollection.removeWords(serverID, author.getId(), words);
                    channel.sendMessage("Successfully deleted the following word(s): " + deleted).queue();
                }

                else if(content.startsWith(GET_COMMAND)){
                    String words = wordCollection.getMonitoredWords(serverID, author.getId());
                    PrivateChannel privateChannel = author.openPrivateChannel().complete();

                    if(words != null)
                        privateChannel.sendMessage("Your monitored words in "
                                + event.getGuild().getName() + " are: " + words).queue();
                    else
                        privateChannel.sendMessage("You are not monitoring any words in "
                            + event.getGuild().getName() ).queue();
                }

                //See if the message has any specified words
                else {

                    HashMap<String, Entry> map = wordCollection.checkWords(serverID, content);

                    //Send private messages to person specifying what words were said and the whole message
                    if (map != null) {

                        //Don't alert the author of a message if they use one of their monitored words
                        map.remove( author.getId() );

                        MessageEmbed embed = createMessageEmbed(author, member, content);
                        for (Entry entry : map.values()) {

                            User user = new EntityBuilder(event.getJDA())
                                    .createUser(entry.getUser());

                            PrivateChannel privateChannel = user.openPrivateChannel().complete();
                            privateChannel.sendMessage("Message in " + event.getGuild().getName() + " <" + channel.getName()
                                    + "> containing the following keyword(s) was detected: " + entry.getBuilder()).queue();
                            privateChannel.sendMessage(embed).queue();
                        }
                    }
                }
            }
        }
    }

    /**
     * Creates an embed that can be sent to alert a user on Discord.
     * @param author The author of the message that contained a monitored word.
     * @param member The author of the message.
     * @param content String containing the message.
     * @return A MessageEmbed to be sent to the person to be alerted.
     */

    private MessageEmbed createMessageEmbed(User author, Member member, String content){
        String authorName = (author.getName().equals(member.getEffectiveName())) ? author.getName() :
                member.getEffectiveName() + " (" + author.getName() + ")";

        EmbedBuilder builder = new EmbedBuilder()
                .setColor(Color.RED)
                .setAuthor(authorName)
                .setFooter(content, author.getAvatarUrl())
                .setTimestamp(OffsetDateTime.now());
        return builder.build();
    }

    /**
     * Formats a message so it may be properly parsed by other functions.
     * @param command The command to trim from the message
     * @param content The message.
     * @return The formatted message.
     */

    private String retrieveArgs(String command, String content){
        return content.replace(command, "").toLowerCase().trim();
    }
}
