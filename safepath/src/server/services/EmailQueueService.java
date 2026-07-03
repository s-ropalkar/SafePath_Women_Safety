package server.services;

import java.util.concurrent.*;

/**
 * Async email delivery — HTTP handlers enqueue; background worker sends via SMTP.
 */
public final class EmailQueueService {

  private static EmailQueueService instance;
  private final BlockingQueue<EmailJob> queue = new LinkedBlockingQueue<>();
  private final EmailService emailService = new EmailService();
  private volatile boolean running = true;

  private EmailQueueService() {
    Thread worker = new Thread(this::runWorker, "safepath-email-worker");
    worker.setDaemon(true);
    worker.start();
  }

  public static synchronized EmailQueueService get() {
    if (instance == null) instance = new EmailQueueService();
    return instance;
  }

  public void enqueue(String to, String subject, String body) {
    queue.offer(new EmailJob(to, subject, body));
  }

  public int enqueueBatch(java.util.List<String> emails, String subject, String body) {
    for (String email : emails) {
      enqueue(email, subject, body);
    }
    return emails.size();
  }

  private void runWorker() {
    while (running) {
      try {
        EmailJob job = queue.poll(1, TimeUnit.SECONDS);
        if (job == null) continue;
        emailService.sendSync(job.to, job.subject, job.body);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      } catch (Exception e) {
        System.err.println("[EmailQueue] Worker error: " + e.getMessage());
      }
    }
  }

  private record EmailJob(String to, String subject, String body) {}
}
