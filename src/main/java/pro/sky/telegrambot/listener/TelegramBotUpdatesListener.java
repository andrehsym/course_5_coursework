package pro.sky.telegrambot.listener;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import pro.sky.telegrambot.models.NotificationTask;
import pro.sky.telegrambot.repository.NotificationTaskRepository;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TelegramBotUpdatesListener implements UpdatesListener {

    private Logger logger = LoggerFactory.getLogger(TelegramBotUpdatesListener.class);

    private TelegramBot telegramBot;
    private final NotificationTaskRepository notificationTaskRepository;

    public TelegramBotUpdatesListener(NotificationTaskRepository notificationTaskRepository, TelegramBot telegramBot) {
        this.notificationTaskRepository = notificationTaskRepository;
        this.telegramBot = telegramBot;
    }

    @PostConstruct
    public void init() {
        telegramBot.setUpdatesListener(updates -> {
            process(updates);
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });
    }

    @Override
    public int process(List<Update> updates) {
        updates.forEach(update -> {
            logger.info("Processing update: {}", update);
            if (update.message().text().equals("/start")) {
                startMessage(update);
            }
            else {
                parsingNotifications(update);
            }
        });
        return UpdatesListener.CONFIRMED_UPDATES_ALL;
    }

    private void startMessage(Update update) {
        SendMessage request = new SendMessage(update.message().chat().id(), "Привет! " +
                "\nЧтобы создать напоминание, напиши: " +
                "\nчисло.месяц.год точное:время текст напоминания" +
                "\nНапример:" +
                "\n05.01.2022 20:00 Сесть за домашнюю работу");
        SendResponse sendResponse = telegramBot.execute(request);
    }

    public void parsingNotifications(Update update) {
        Pattern pattern = Pattern.compile("([0-9\\.\\:\\s]{16})(\\s)([\\W+]+)");
        Matcher matcher = pattern.matcher(update.message().text());
        try {
            matcher.matches();
            String date = matcher.group(1);
            String item = matcher.group(3);
            LocalDateTime dateTime = LocalDateTime.parse(date, DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
            NotificationTask notify = new NotificationTask();
            notify.setChatId(update.message().chat().id());
            notify.setNotification(item);
            notify.setDateTime(dateTime);
            notificationTaskRepository.save(notify);
            SendMessage request = new SendMessage(update.message().chat().id(), "Напоминание создано");
            SendResponse sendResponse = telegramBot.execute(request);
        } catch (IllegalStateException e) {
            SendMessage request = new SendMessage(update.message().chat().id(), "Неправильный формат напоминания");
            SendResponse sendResponse = telegramBot.execute(request);
        } catch (DateTimeParseException e) {
            SendMessage request = new SendMessage(update.message().chat().id(), "Неправильный формат даты или времени");
            SendResponse sendResponse = telegramBot.execute(request);
        }
    }

    @Scheduled(cron = "0 0/1 * * * *")
    public void sendNotifications() {
        List<NotificationTask> actualNotifications = notificationTaskRepository.findByDateTimeEquals(LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES));
        for (NotificationTask notify: actualNotifications) {
            SendMessage actualNotification = new SendMessage(notify.getChatId(), notify.getNotification());
            SendResponse sendNotification = telegramBot.execute(actualNotification);
            notificationTaskRepository.deleteById(notify.getId());
        }
    }
}

