package com.task;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

class Task{
    String id;
    int burstTime;
    List<String> memBlocks;
    int memId;
    int waitCycles;

    public Task(String id, int burstTime, List<String> memBlocks) {
        this.id = id;
        this.burstTime = burstTime;
        this.memBlocks = new ArrayList<>(memBlocks);
        this.memId = 0;
        this.waitCycles = 0;
    }    
}

class Cache{
    String name;
    int capacity;
    int latency;
    Deque<String> blocks;

    public Cache(String name, int capacity, int latency) {
        this.name = name;
        this.capacity = capacity;
        this.latency = latency;
        this.blocks = new ArrayDeque<>();
    }

    public boolean contains(String block){
        return blocks.contains(block);
    }

    public String add(String block){
        String evict = "";
        if(blocks.size() >= capacity){
            evict = blocks.removeFirst();
        }

        if(!contains(block)) blocks.addLast(block);
        return evict;
    }

    public String getStateString(){
        return "[" + String.join(", ", blocks) + "]";
    }
}

class MemoryHeirarchy{
    Cache L1 = new Cache("L1", 32, 4);
    Cache L2 = new Cache("L2", 128, 12);
    Cache L3 = new Cache("L3", 512, 40);
    int ramAccess = 0;

    public int requestBlock(String block, StringBuilder log){
        log.append("L1: ").append(L1.getStateString());
        if(L1.contains(block)){
            log.append(" -> HIT (").append(L1.latency).append(" cycles)\n");
            log.append("L2: ").append(L2.getStateString()).append("\n");
            log.append("L3: ").append(L3.getStateString());
            return L1.latency;
        }
        log.append(" >> MISS\n");

        log.append("L2: ").append(L1.getStateString());
        if(L2.contains(block)){
            log.append(" -> HIT (").append(L2.latency).append(" cycles)\n");
            log.append("promoting ").append(block).append(" -> L1");
            String evict = L1.add(block);
            log.append("L1: ").append(L1.getStateString()).append("\n");
            if(!evict.isEmpty()) log.append(" (").append(evict).append(" evicted)");
            log.append("\nL3: ").append(L3.getStateString());
            return L2.latency;
        }
        log.append(" >> MISS\n");

        log.append("L3: ").append(L3.getStateString());
        if(L3.contains(block)){
            log.append(" -> HIT (").append(L3.latency).append(" cycles)\n");
            log.append("promoting ").append(block).append(" -> L1");
            String evict = L1.add(block);
            log.append("L1: ").append(L1.getStateString()).append("\n");
            if(!evict.isEmpty()) log.append(" (").append(evict).append(" evicted)");
            return L3.latency;
        }
        log.append(" >> MISS\n");

        ramAccess++;
        int latency = 200;
        log.append("Fetching from RAM (").append(latency).append(" cycles)\n");
        String evict = L1.add(block);
        log.append("L1: ").append(L1.getStateString());
        if(!evict.isEmpty()) log.append(" (").append(evict).append(" evicted)");
        else log.append("\n");
        log.append("L2: ").append(L2.getStateString()).append("\n");
        log.append("L3: ").append(L3.getStateString());
        return latency;
    }
}

class Scheduler{
    Queue<Task> readyQueue = new LinkedList<>();
    MemoryHeirarchy mem = new MemoryHeirarchy();
    int quantum;
    int totalCycle = 1;
    int taskComp = 0;

    public Scheduler(int quantum) {
        this.quantum = quantum;
    }

    public void loadTask(Task t){
        readyQueue.add(t);
    }   
    
    public void run(){
        Task curr = null;
        int currQuantum = 0;

        while(!readyQueue.isEmpty() || curr!= null){
            if(curr == null){
                curr = readyQueue.poll();
                currQuantum = quantum;
            }

            if(curr.waitCycles > 0){
                curr.waitCycles--;
                totalCycle++;
                continue;
            }

            if(curr.memId < curr.memBlocks.size()){
                String req = curr.memBlocks.get(curr.memId);
                System.out.println("Cycles " + totalCycle + " - Running: " + curr.id + " Requesting: " + req);
                
                StringBuilder log = new StringBuilder();
                int lat = mem.requestBlock(req, log);
                System.out.println(log.toString() + "\n");

                curr.memId++;
                curr.waitCycles = lat - 1;
            }
            else if(curr.burstTime > 0){
                curr.burstTime--;
                currQuantum--;
            }

            totalCycle++;

            if(curr.burstTime <= 0 && (curr.memId == curr.memBlocks.size())){
                taskComp++;
                curr = null;
            }
            else if(currQuantum == 0 && curr.waitCycles == 0){
                readyQueue.add(curr);
                curr = null;
            }
        }

        System.out.println("-----Finals Result-----");
        System.out.println("Total Cycles : " + (totalCycle - 1));
        System.out.println("Tasks Completed: " + taskComp);
        System.out.println("Scheduler : Round Robin (quantum " + quantum + ")");
        System.out.println("RAM ACCESSES : " + mem.ramAccess);
    }
}

public class CacheSimulator{
    public static void main(String[] args) {
        Scheduler scheduler = new Scheduler(3);

        String fileName = "input_task2.txt";
        try{
            BufferedReader br = new BufferedReader(new FileReader(fileName));
            String line;
            while((line = br.readLine()) != null){
                line = line.trim();
                if(line.isEmpty()) continue;

                String[] parts = line.split("\\s+");
                if(parts.length < 4) continue;

                String id = parts[1];
                int burst = Integer.parseInt(parts[3]);
                List<String> mems = new ArrayList<>();

                if(parts.length > 4 && parts[4].equals("MEM")){
                    for(int i=5; i<parts.length; i++){
                        mems.add(parts[i]);
                    }
                }
                scheduler.loadTask(new Task(id, burst, mems));
            }
            br.close();
        }
        catch(IOException e){
            System.err.println("Error reading file: " + e.getMessage());
            return;
        }

        scheduler.run();
    }
}