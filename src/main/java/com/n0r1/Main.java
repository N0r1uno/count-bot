package com.n0r1;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.request.SendMessage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class Main {

    private final static Pattern pattern = Pattern.compile("work[\\s|\n]*(\\d{2}:\\d{2})\\s*-\\s*(\\d{2}:\\d{2})");
    private final static Map<Long, List<Pair>> db = new HashMap<>();

    public static void main(String[] args) throws IOException {
        loadDb();
        TelegramBot bot = new TelegramBot(Files.readString(Path.of("token.txt")));

        bot.setUpdatesListener(updates -> {
            updates.forEach(update -> {

                try {
                    final long id = update.message().from().id();
                    final String text = update.message().text().toLowerCase();
                    if (text.contains("finalize")) {
                        respond(bot, id, finalize(id));
                    } else {
                        final Pair parsed = parseText(text);
                        if (parsed != null) {
                            addPair(update.message().from().id(), parsed);
                            respond(bot, id, "Added " + parsed);
                        } else respond(bot, id, answers[new Random().nextInt(answers.length)]);
                    }
                } catch (Exception e) {
                    bot.execute(new SendMessage(update.updateId(), "bin kaputt hau ab"));
                }

            });
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });
    }

    static void loadDb() throws IOException {
        Path of = Path.of("db.csv");
        if (!Files.exists(of)) {
            Files.writeString(of, "id;start;end\n");
        }
        try (Stream<String> s = Files.lines(of)) {
            s.skip(1)
                    .map(line -> line.split(";"))
                    .forEach(parts -> {
                        final long id = Long.parseLong(parts[0]);
                        if (parts.length == 3) {
                            final LocalDateTime start = LocalDateTime.parse(parts[1]), end = LocalDateTime.parse(parts[2]);
                            db.computeIfAbsent(id, k -> new LinkedList<>()).add(new Pair(start, end));
                        } else {
                            db.remove(id);
                        }
                    });
        }
    }

    static void respond(TelegramBot bot, long chatId, String text) {
        bot.execute(new SendMessage(chatId, text));
    }

    static Pair parseText(String text) {
        text = text.toLowerCase();
        // match pattern
        final Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            try {
                final LocalDate now = LocalDate.now();
                final LocalTime start = LocalTime.parse(matcher.group(1));
                final LocalTime end = LocalTime.parse(matcher.group(2));
                return new Pair(LocalDateTime.of(now, start), LocalDateTime.of(now, end));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    static void addPair(long id, Pair pair) throws IOException {
        db.computeIfAbsent(id, k -> new LinkedList<>()).add(pair);
        Files.writeString(Path.of("db.csv"),
                String.format("%d;%s;%s\n", id, pair.start, pair.end),
                StandardOpenOption.APPEND);
    }

    static String finalize(long id) {
        if (!db.containsKey(id))
            return "No data found";

        StringBuilder sb = new StringBuilder();
        sb.append("All hours since last reset:\n");
        db.get(id).forEach(pair -> sb.append(pair).append("\n"));
        sb.append("Total: ");
        long total = db.get(id).stream().mapToLong(Pair::getDurationInSeconds).sum();
        sb.append(total / 3600).append("h ").append(total % 3600 / 60).append("m");
        db.remove(id);
        try {
            Files.writeString(Path.of("db.csv"), String.format("%d;%s\n", id, "reset"), StandardOpenOption.APPEND);
        } catch (IOException e) {
            e.printStackTrace();
            sb.append("\n!Failed to reset database!");
        }

        return sb.toString();
    }

    static class Pair {
        final LocalDateTime start, end;

        Pair(LocalDateTime start, LocalDateTime end) {
            this.start = start;
            this.end = end;
        }

        long getDurationInSeconds() {
            return Duration.between(start, end).getSeconds();
        }

        @Override
        public String toString() {
            DateTimeFormatter h = DateTimeFormatter.ofPattern("HH:mm"), d = DateTimeFormatter.ofPattern("dd.MM.yyyy");
            return start.format(d) + " " + start.format(h) + " - " + end.format(h)
                    + " (" + getDurationInSeconds() / 3600 + "h " + getDurationInSeconds() % 3600 / 60 + "m)";
        }
    }

    //hust hust
    private final static String[] answers = new String[]{"Wasch labersch?", "Halts Maul", "Nochmal du Penner", "Ich bin zu doof"};
}