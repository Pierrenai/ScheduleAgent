package jadelab2;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.*;

/*
/!\ If you want to make this agent work with others that don't have the same functionality,
    you'll need to review the message verification system. (MessageTemplate)
 */

public class ScheduleAgent extends Agent {

    private List<String> friendsList = new ArrayList<>();
    private Hashtable<String, Float> ownDisponibilitiesList = new Hashtable();

    protected void setup() {
        // service registration at DF
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("schedule-setting");
        sd.setName("JADE-schedule-setting");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        }
        catch (FIPAException fe) {
            fe.printStackTrace();
        }

        addBehaviour(new ScheduleAgent.AppointmentReceive());
        addBehaviour(new ScheduleAgent.AppointmentPerformer());

        ownDisponibilitiesList = ownDisponibilitiesListGeneretor();
        //System.out.println(getAID().getLocalName() + ", ownDisponibilitiesList: " + ownDisponibilitiesList);

        Object[] args = getArguments();
        // if condition true the agent is the issuer of an appointment otherwise nothing happens
        if (args != null && args.length > 0 && Boolean.parseBoolean(args[0].toString())) {
            addBehaviour(new OneShotBehaviour() {
                 @Override
                 public void action() {

                    //update a list of known friends (DF)
                    DFAgentDescription template = new DFAgentDescription();
                    ServiceDescription sd = new ServiceDescription();
                    sd.setType("schedule-setting");
                    template.addServices(sd);
                    try {
                        DFAgentDescription[] result = DFService.search(myAgent, template);
                        for (int i = 0; i < result.length; ++i) {
                            friendsList.add(result[i].getName().getName());
                        }
                        System.out.println(getAID().getLocalName() + ": the following friends have been found " + friendsList);

                    } catch (FIPAException fe) {
                        fe.printStackTrace();
                    }

                    addBehaviour(new ScheduleAgent.AppointmentEmiter());
                }
            });
        }

    }

    protected void takeDown() {
        //service deregistration at DF
        try {
            DFService.deregister(this);
        }
        catch (FIPAException fe) {
            fe.printStackTrace();
        }
        System.out.println("Agent " + getAID().getName() + " terminated.");
    }

    /*
    Emiting an appointment
     */
    private class AppointmentEmiter extends Behaviour {
        //private List<String> participantsList = new ArrayList<>();
        private String messageContent = "";
        private MessageTemplate mt;
        private int step = 0;

        public void action() {
            switch (step) {
                case 0:
                    List<String> participantsList = new ArrayList<>();
                    for(int i=0; i<friendsList.size(); i++){
                        participantsList.add(friendsList.get(i));
                    }

                    // Sending appointment proposal
                    ACLMessage message = new ACLMessage(ACLMessage.REQUEST);

                    // Removing the sender from the list
                    participantsList.remove(meetingCreator);

                    // Get random friends in list
                    String randomFriend = getRandomFromList(participantsList);
                    message.addReceiver(new AID(randomFriend));

                    // Removing the receiver from the list (because he doesn't need to send a verification to him self)
                    participantsList.remove(randomFriend);


                    // Get 5 appointment date
                    Hashtable<String, Float> ht = new Hashtable<>();
                    float one = 1;
                    ht.put("2023-01-13-17", one);
                    ht.put("2023-01-31-17", one);
                    ht.put("2023-01-13-16", one);
                    ht.put("2023-01-31-16", one);
                    ht.put("2023-01-13-15", one);

                    // Creating the message
                    // Format : meetingCreator,respondentNumber,participantsList,averageDisponibilitiesList
                    messageContent = meetingCreator.getName() + "£" + 0 + "£" + participantsList + "£" + ht;
                    //System.out.println("Message : " + messageContent);

                    message.setContent(messageContent);
                    myAgent.send(message);

                    step=1;
                    break;
                case 1:
                    // Waiting for appointment to be calculated
                    mt = MessageTemplate.MatchPerformative(ACLMessage.PROPOSE);

                    // Receive a response
                    ACLMessage reply = myAgent.receive(mt);
                    if (reply != null) {
                        String content = reply.getContent();
                        Hashtable<String, Float> averageDisponibilitiesList = (Hashtable<String, Float>) dateStringToHashtable(content);

                        String keyMaxValue = "";
                        float maxValue = 0;

                        // Averaging the disponibilities list
                        Enumeration<String> e = averageDisponibilitiesList.keys();
                        while (e.hasMoreElements()) {
                            String key = e.nextElement();
                            float value = averageDisponibilitiesList.get(key);
                            if(value>maxValue) {
                                maxValue = value;
                                keyMaxValue = key;
                            }
                        }
                        message = new ACLMessage(ACLMessage.INFORM);

                        for(int i=0; i<friendsList.size(); i++){
                            message.addReceiver(new AID(friendsList.get(i)));
                        }
                        message.setContent(keyMaxValue);
                        myAgent.send(message);
                        step=9;
                    }
                    else {
                        block();
                    }
                    break;
            }
        }

        public boolean done() {
            if(step==9) {
                System.out.println("End of making appointment");
                return true;
            } else {
            return false;
            }
        }
    }

    private class AppointmentPerformer extends CyclicBehaviour {
        public void action() {
                //proposals only template
                MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
                ACLMessage received_msg = myAgent.receive(mt);
                if (received_msg != null) {
                    String content = received_msg.getContent();
                    Hashtable<String, Object> ht = contentToHashtable(content);

                    // Message decomposition
                    AID meetingCreator = new AID(ht.get("meetingCreator").toString());
                    int respondentNumber = (int) ht.get("respondentNumber");
                    List<String> participantsList = stringToList(ht.get("participantsList").toString());
                    Hashtable<String, Float> averageDisponibilitiesList = (Hashtable<String, Float>) ht.get("averageDisponibilitiesList");

                    /*
                    Sends the list of people and the list of dates to a random person and the name of the
                    meeting and the number of people who responded
                    Modification of the average based on his date list and the number of people who responded
                    Removes his name from the list and adds 1 to the number of people who responded

                    If the list is empty, send back to the emeteur, otherwise send to a random person ...
                     */

                    // Averaging the disponibilities list
                    Enumeration<String> e = averageDisponibilitiesList.keys();
                    while (e.hasMoreElements()) {
                        String key = e.nextElement();
                        float actualAverage = averageDisponibilitiesList.get(key);
                        float myValue = ownDisponibilitiesList.get(key);
                        averageDisponibilitiesList.put(key, average(actualAverage, myValue, respondentNumber));
                    }

                    ACLMessage message;
                    String randomFriend = getRandomFromList(participantsList);

                    if (!randomFriend.equals("")) {
                        // Passe au suivant
                        message = new ACLMessage(ACLMessage.REQUEST);

                        // Sending to random

                        message.addReceiver(new AID(randomFriend));

                        // Removing the receiver from the list (because he doesn't need to send a verification to himself)
                        participantsList.remove(randomFriend);

                        // Add 1 to respondentNumber
                        respondentNumber++;

                        message.setContent(meetingCreator.getName() + "£" + respondentNumber + "£" + participantsList + "£" + averageDisponibilitiesList);
                    }
                    else {
                        // Retrun to the Appointment Performer
                        message = new ACLMessage(ACLMessage.PROPOSE);
                        message.addReceiver(meetingCreator);

                        message.setContent(averageDisponibilitiesList.toString());

                        System.out.println("End of averaging");
                    }
                    myAgent.send(message);
                }
                else {
                    block();
                }
        }
    }


    private class AppointmentReceive extends CyclicBehaviour {
        public void action() {
            //purchase order as proposal acceptance only template
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
                String content = msg.getContent();
                System.out.println(this.getAgent().getLocalName() + ", Appointment is on: " + content);
            }
            else {
                block();
            }
        }
    }

    /*
     Date format year-month-day-hour | 0000-00-00-00
     Generate randomly disponibilities from 2023-01-10-10 to 2023-01-31-17
     With opening hour 10 to 18

     /!\ Be careful if you want dates containing only one digit, you will have to modify the code.
     */
    private Hashtable<String, Float> ownDisponibilitiesListGeneretor() {
        Hashtable<String, Float> ht = new Hashtable();
        for(int i=10; i<=31; i++){
            for(int j=10; j<=17; j++){
                String date = "2023-01-" + i + "-" + j;
                String random = Math.random() + "";
                ht.put(date, Float.parseFloat(random));
            }
        }

        return ht;
    }

    private float average(float actualAverage, float myValue, int respondentNumber) {
        return ((actualAverage * respondentNumber) + myValue)/(respondentNumber+1);
    }

    /*
    This part convert the message to a hashtable easier to work with.
     */
    public Hashtable<String, Object> contentToHashtable(String content){

        Hashtable<String, Object> result = new Hashtable<String, Object>();
        String temp[] = content.split("£");

        System.out.println(Arrays.toString(temp));

        result.put("meetingCreator",  new AID(temp[0]).getName());

        result.put("respondentNumber", Integer.parseInt(temp[1]));

        result.put("participantsList", temp[2]);

        result.put("averageDisponibilitiesList", dateStringToHashtable(temp[3]));

        return result;
    }

    // All in the name
    public Hashtable dateStringToHashtable(String s){
        Hashtable<String, Float> ht = new Hashtable();

        s = s.substring(1, s.length() -1);

        String[] pairs = s.split(",");

        for (String pair : pairs) {
            String[] keyValue = pair.split("=");

            ht.put(keyValue[0].trim(), Float.parseFloat(keyValue[1].trim()));
        }

        return ht;
    }

    public List<String> stringToList(String s){
        List<String> list = new ArrayList<>();

        s = s.substring(1, s.length() -1);

        String[] strings = s.split(",");

        for (String string : strings) {
            list.add(string);
        }

        return list;
    }

    public String getRandomFromList(List<String> list) {
        Random rand = new Random();
        String s = list.get(rand.nextInt(list.size()));

        return s;
    }

}
