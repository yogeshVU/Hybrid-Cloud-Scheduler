package Scheduler;

import Entity.Agent;
import Entity.Job;
import JobMananger.SparkLauncherAPI;
import Operator.ServerResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.logging.Level;

public class BestFitScheduler extends Thread {

    private static ServerResponse resObj;
    private static long placementTime;

    class AgentComparator implements Comparator<Agent> {
        @Override
        public int compare(Agent a, Agent b) {

            //to sort agents in an increasing order of resource capacity (small to big)
            if (a.getResourceTotal() < b.getResourceTotal()) {
                return -1;
            } else if (a.getResourceTotal() > b.getResourceTotal()) {
                return 1;
            } else {
                return 0;
            }
        }
    }

    class JobComparator implements Comparator<Job> {
        @Override
        public int compare(Job a, Job b) {

            //to sort jobs in a decreasing order of resource demands (big to small)
            if (a.getResourceSplit() > b.getResourceSplit() ) {
                return -1;
            } else if (a.getResourceSplit()  < b.getResourceSplit() ) {
                return 1;
            } else {
                return 0;
            }
        }
    }


    public void run() {

        boolean shutdown= false;

        while(true) {

            if (shutdown&&SchedulerUtil.fullySubmittedJobList.size()==0) {
                Log.SchedulerLogging.log(Level.INFO, BestFitScheduler.class.getName() + "Shutting Down BestFit Scheduler. Job Queue is Empty...");
                SchedulerManager.shutDown();
                break;
            }
            synchronized (SchedulerUtil.jobQueue) {
                synchronized (SchedulerUtil.fullySubmittedJobList) {
                    synchronized (SchedulerUtil.agentList) {

                        //update agents
                        StatusUpdater.updateAgents();

                        //sort all the Agents according to increasing resource capacity
                        Collections.sort(SchedulerUtil.agentList, new AgentComparator());

                        //update jobs
                        StatusUpdater.updateJobs();


                        //sort all the Jobs according to decreasing resource requirements
                        Collections.sort(SchedulerUtil.jobQueue, new JobComparator());

                        Job currentJob;

                        //Log.SchedulerLogging.log(Level.INFO,BestFitScheduler.class.getName()+": Trying to place executors for jobs from JobQueue");

                        for (int i = 0; i < SchedulerUtil.jobQueue.size(); i++) {

                            currentJob = SchedulerUtil.jobQueue.get(i);

                            //shutDown check
                            if (currentJob.isShutdown()) {
                                if (SchedulerUtil.jobQueue.size() == 1&&SchedulerUtil.fullySubmittedJobList.size()==0) {
                                    shutdown = true;
                                }
                            }
                            else {
                                if (placeExecutor(currentJob,BestFitScheduler.class)) {
                                    Log.SchedulerLogging.log(Level.INFO, BestFitScheduler.class.getName() + ": Placed executor(s) for Job: " + currentJob.getJobID());
                                    currentJob.setResourceReserved(true);
                                    currentJob.setSchedulingDelay(currentJob.getSchedulingDelay()+placementTime);

                                    if (currentJob.getAllocatedExecutors() == currentJob.getExecutors()) {
                                        Log.SchedulerLogging.log(Level.INFO, BestFitScheduler.class.getName() + ": All executors are placed for Job: " + currentJob.getJobID());
                                        //remove job from job queue
                                        SchedulerUtil.jobQueue.remove(i);
                                        Log.SchedulerLogging.log(Level.INFO, BestFitScheduler.class.getName() + ": Removed Job: " + currentJob.getJobID() + " from jobQueue");
                                        //add job to fully submitted job list
                                        SchedulerUtil.fullySubmittedJobList.add(currentJob);
                                        Log.SchedulerLogging.log(Level.INFO, BestFitScheduler.class.getName() + ": Added Job: " + currentJob.getJobID() + " to fullySubmittedJobList");
                                        i--;
                                    }
                                } else {
                                    //could not place any executors for the current job
                                    //Log.SchedulerLogging.log(Level.INFO, BestFitScheduler.class.getName() + ":Could not place any executor(s) for Job: " + currentJob.getJobID());
                                }
                            }

                        }


                        //Submitting new jobs (with fully placed executors) to the cluster
                        for(int i=0;i<SchedulerUtil.fullySubmittedJobList.size();i++)
                        {
                            currentJob = SchedulerUtil.fullySubmittedJobList.get(i);
                            //if the job is new submit it in the cluster
                            if (!currentJob.isSubmitted()&&currentJob.isResourceReserved()) {
                                currentJob.setSubmitted(true);
                                Log.SchedulerLogging.log(Level.INFO, BestFitScheduler.class.getName() + ": Submitting Job: " + currentJob.getJobID() +" with role: "+currentJob.getRole()+ " to the Cluster");
                                new SparkLauncherAPI(currentJob).start();
                            }
                        }
                    }
                }
            }

            //sleep
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    static boolean placeExecutor(Job currentJob, Class classVar)  {

        int executorCount=0;
        ArrayList<Agent> placedAgents = new ArrayList<>();
        placementTime=System.currentTimeMillis();

        for(int i=0;i<SchedulerUtil.agentList.size();i++) {

            while(true) {

                if (SchedulerUtil.agentList.get(i).getCpu() >= currentJob.getCoresPerExecutor() &&
                        SchedulerUtil.agentList.get(i).getMem() >= Math.ceil(currentJob.getTotalExecutorMemory())) {

                    SchedulerUtil.agentList.get(i).setCpu(SchedulerUtil.agentList.get(i).getCpu() - currentJob.getCoresPerExecutor());
                    SchedulerUtil.agentList.get(i).setMem(SchedulerUtil.agentList.get(i).getMem() - Math.ceil(currentJob.getTotalExecutorMemory()));
                    placedAgents.add(SchedulerUtil.agentList.get(i));
                    executorCount++;
                }
                else {
                    break;
                }
                // all the executors for the current job is placed
                if (executorCount == currentJob.getExecutors()){
                    break;
                }
            }
        }

        // All the executors of the current-job can be placed
        // So Reserve Resources in the Agents
        if (executorCount == currentJob.getExecutors() ){
            currentJob.setAllocatedExecutors(currentJob.getExecutors());
            SchedulerUtil.resourceReservation(placedAgents,currentJob,classVar);
            placementTime=System.currentTimeMillis()-placementTime;
            return true;
        }
        //Could not place all the executors, take back the resources in Agent data structure..
        //Also set the initial position of the index
        else {
            for(int i=0;i<placedAgents.size();i++){
                placedAgents.get(i).setCpu(placedAgents.get(i).getCpu() + currentJob.getCoresPerExecutor());
                placedAgents.get(i).setMem(placedAgents.get(i).getMem() + Math.ceil(currentJob.getTotalExecutorMemory()));
            }
            return false;
        }
    }
}
