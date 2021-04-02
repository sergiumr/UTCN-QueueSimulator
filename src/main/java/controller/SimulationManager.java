package controller;

import model.Client;
import model.Queue;
import validator.AscendingArrivalTimeSort;
import validator.DurationFormatter;
import view.Person;
import view.View;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class SimulationManager implements Runnable {
   private final View appView;
   private final Controller appController;
   private final int queuesNumber;
   private final int clientsNumber;
   private final int simulationTime;
   private final int minArrivalTime;
   private final int maxArrivalTime;
   private final int minServiceTime;
   private final int maxServiceTime;
   private ExecutorService executorService;
   private Scheduler queueScheduler;
   private CopyOnWriteArrayList<Client> waitingClients;
   private FileWriter finalResults;

   public SimulationManager(int queuesNumber, int clientsNumber, int simulationTime, int minArrivalTime, int maxArrivalTime, int minServiceTime, int maxServiceTime, View appView, Controller appController) {
      this.appView = appView;
      this.appController = appController;
      this.queuesNumber = queuesNumber;
      this.clientsNumber = clientsNumber;
      this.simulationTime = simulationTime;
      this.minArrivalTime = minArrivalTime;
      this.maxArrivalTime = maxArrivalTime;
      this.minServiceTime = minServiceTime;
      this.maxServiceTime = maxServiceTime;
      this.initializeSimulation();
   }

   private void initializeSimulation() {
      this.waitingClients = generateRandomClients();
      CopyOnWriteArrayList<Queue> availableQueues = new CopyOnWriteArrayList<>();
      try {
         finalResults = new FileWriter("results.txt");
         queueScheduler = new Scheduler(appView, availableQueues);
      } catch (IOException e) {
         e.printStackTrace();
      }
      executorService = Executors.newFixedThreadPool(queuesNumber);
      for (int i = 0; i < queuesNumber; i++) {
         Queue newQueue = new Queue(appView, appController, i);
         availableQueues.add(newQueue);
         executorService.execute(newQueue);
         appView.addPerson(i, new Person(null));
      }
   }

   private CopyOnWriteArrayList<Client> generateRandomClients() {
      CopyOnWriteArrayList<Client> generatedClients = new CopyOnWriteArrayList<>();
      for (int i = 0; i < clientsNumber; i++) {
         int randomArrivalTime = (int) (Math.random() * (maxArrivalTime - minArrivalTime + 1) + minArrivalTime);
         int randomServiceTime = (int) (Math.random() * (maxServiceTime - minServiceTime + 1) + minServiceTime);
         Client newClient = new Client(i + 1, randomArrivalTime, randomServiceTime);
         generatedClients.add(newClient);
      }
      generatedClients.sort(new AscendingArrivalTimeSort());
      return generatedClients;
   }

   @Override
   public void run() {
      final AtomicInteger currentTime = new AtomicInteger(0);
      final AtomicInteger totalWaitingTime = new AtomicInteger(0);
      final AtomicInteger peakHour = new AtomicInteger(0);
      final AtomicInteger peakClients = new AtomicInteger(0);
      final double averageServiceTime = this.getAverageServiceTime();

      appView.showTimer(true);
      Timer runningSimulation = new Timer();
      runningSimulation.schedule(new TimerTask() {
         double averageWaitingTime = 0;

         @Override
         public void run() {
            if (currentTime.get() > simulationTime || !appController.isSimulationRunning().get() || (waitingClients.isEmpty() && totalWaitingTime.get() == 0)) {
               System.out.println("dea");
               cancel();
               appController.setSimulationRunning(false);
               String computedResults = String.format("Average waiting time %.2f | ", averageWaitingTime / clientsNumber);
               computedResults += String.format("Average service time %.2f | ", averageServiceTime);
               computedResults += "Peek hour " + DurationFormatter.getTimerFormat(peakHour.get());
               appView.setSimulationResults(computedResults, false);
               stopSimulation();
            } else {
               CopyOnWriteArrayList<Client> toRemove = new CopyOnWriteArrayList<>();
               for (Client currentClient : waitingClients) {
                  if (currentClient.getArrivalTime() == currentTime.get()) {
                     averageWaitingTime += queueScheduler.sendClientToQueue(currentClient);
                     toRemove.add(currentClient);
                  }
               }
               waitingClients.removeAll(toRemove);
               totalWaitingTime.set(queueScheduler.getTotalWaitingTime().get());
               appView.setTimer(currentTime.get());
               writeSimulationStatusToFile(currentTime.get());
               getPeak(peakHour, peakClients, currentTime);
               currentTime.incrementAndGet();
            }
         }
      }, 0, 1000);
   }

   private double getAverageServiceTime() {
      double averageServiceTime = 0d;
      for(Client crtClient : waitingClients) {
         averageServiceTime += crtClient.getServiceTime();
      }
      averageServiceTime /= waitingClients.size();
      return averageServiceTime;
   }

   private void writeSimulationStatusToFile(int currentTime) {
      try {
         finalResults.write("----- Time " + DurationFormatter.getTimerFormat(currentTime) + " -----\n");
         finalResults.write("Waiting clients: ");

         if (waitingClients.isEmpty()) {
            finalResults.write("none\n");
         } else {
            for (Client currentClient : waitingClients) {
               finalResults.write(currentClient.toString() + " ");
            }
            finalResults.write("\n");
         }

         for (Queue crtQueue : queueScheduler.getAvailableQueues()) {
            finalResults.write(crtQueue.toString());
         }
      } catch (IOException e) {
         e.printStackTrace();
      }
   }

   private void getPeak(AtomicInteger peakHour, AtomicInteger peakClients, AtomicInteger currentTime) {
      int totalClients = 0;
      for(Queue crtQueue : queueScheduler.getAvailableQueues()) {
         totalClients += crtQueue.getClientsNumber().get();
      }
      if (totalClients > peakClients.get()) {
         peakHour.set(currentTime.get());
         peakClients.set(totalClients);
      }
   }

   private void stopSimulation() {
      executorService.shutdown();
      appView.setTimer(0);
      appView.showTimer(false);
      appView.enableButtons(true);
      appView.removeAll();
      try {
         finalResults.close();
      } catch (IOException e) {
         e.printStackTrace();
      }
   }
}
