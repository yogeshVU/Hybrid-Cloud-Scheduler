package Scheduler;

import Entity.Agent;
import Entity.Job;
import java.util.logging.Level;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

public class SchedulerUtil {

    public static String schedulerHome;
    public static String schedulerRole;
    public static int jobHandlerPort;
    public static String schedulerIP;
    public static int schedulerAlgorithm;
    public static Queue<Job> newJobQueue = new LinkedList<>();
    public static ArrayList<Job> partialSubmittedJobList = new ArrayList<>();
    public static ArrayList<Job> fullySubmittedJobList = new ArrayList<>();
    public static ArrayList<Job> finishedJobList = new ArrayList<>();
    public static ArrayList<Agent> agentList;

    public static synchronized Job queueOperation(Job obj, int choice) {

        //add new job in queue
        if(choice==1) {
            newJobQueue.add(obj);
            Log.SchedulerLogging.log(Level.INFO,SchedulerUtil.class.getName()+": New Job Added to job queue with id: " + obj.getJobID());
            return null;
        }
        //inspect the top job in queue
        else if(choice==2) {
            Log.SchedulerLogging.log(Level.INFO,SchedulerUtil.class.getName()+": A Job is being inspected with id: " + newJobQueue.element().getJobID());
            return newJobQueue.element();
        }
        //remove a job from queue
        else {
            Log.SchedulerLogging.log(Level.INFO,SchedulerUtil.class.getName()+" Job " + newJobQueue.element().getJobID() + " is removed from job queue");
            return newJobQueue.remove();
        }
    }

    public static void printJobQueue() {
        for (Object object : newJobQueue) {
            Job jobObj = (Job) object;
            //System.out.println(jobObj.toString());
        }
    }
    public static void printAgentList()
    {
        System.out.println("Printing Agent Details: ");
        for(int i=0;i<agentList.size();i++)
            System.out.println("Agent #"+(i+1)+": "+agentList.get(i).toString());
    }
}