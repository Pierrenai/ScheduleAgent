package jadelab2;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;

public class ScheduleAgent extends Agent {

    private AID[] friendsList;
    private Hashtable<String, Float> ownDisponibilitiesList = new Hashtable();

    protected void setup() {
        //hashtable = new Hashtable(); // /!\ Idée de code

        //book selling service registration at DF
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

        Object[] args = getArguments();
        // if condition true the agent is the issuer of an appointment otherwise nothing happens
        if (args != null && args.length > 0 && Boolean.parseBoolean(args[0].toString())) {

            int interval = 10000;
            boolean appointmentEmiterBool = true;

            String date ="1111-22-33-44";
            //String message = "Moi/12/[Joseph, array, test]/{1111-22-33-44=0.5, 5555-66-77-88=0.6}";
            String message = getAID().getName() + "£12£[Joseph, array, test]£{1111-22-33-44=0.5, 5555-66-77-88=0.6}";

            System.out.println(getAID().getLocalName() + ", result : " + contentToHashtable(message));

            addBehaviour(new TickerBehaviour(this, interval)
            {
                protected void onTick()
                {
                    //search only if the purchase task was ordered
                    if (appointmentEmiterBool)
                    {
                        //update a list of known friends (DF)
                        DFAgentDescription template = new DFAgentDescription();
                        ServiceDescription sd = new ServiceDescription();
                        sd.setType("schedule-setting");
                        template.addServices(sd);
                        try
                        {
                            DFAgentDescription[] result = DFService.search(myAgent, template);
                            System.out.println(getAID().getLocalName() + ": the following friends have been found");
                            friendsList = new AID[result.length];
                            for (int i = 0; i < result.length; ++i)
                            {
                                friendsList[i] = result[i].getName();
                                System.out.println(friendsList[i].getLocalName());
                            }
                        }
                        catch (FIPAException fe)
                        {
                            fe.printStackTrace();
                        }

                        myAgent.addBehaviour(new ScheduleAgent.AppointmentEmiter());
                    }
                }
            });


        }

        ownDisponibilitiesList = ownDisponibilitiesListGeneretor();
        System.out.println(getAID().getLocalName() + ", ownDisponibilitiesList: " + ownDisponibilitiesList);
        //System.out.println(getAID());
    }

    protected void takeDown() {
        //book selling service deregistration at DF
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
        // message content = meetingCreator,respondentNumber,participantsList,averageDisponibilitiesList

        //disponibilitiesHashtable : Hashtable<String (year-month-day-hour), float (0-1))
        private AID meetingCreator = getAID();
        private List<AID> participantsList; // AID ou string ?
        private int respondentNumber;

        private String messageContent = "";

        private MessageTemplate mt;
        private int step = 0;

        public void action() {
            switch (step) {
                case 0:
                    //call for proposal (CFP) to found sellers
                    ACLMessage message = new ACLMessage(ACLMessage.REQUEST);

                    message.setContent(messageContent);
                    message.setConversationId("schedule-set");
                    message.setReplyWith("schedule"+System.currentTimeMillis()); //unique value
                    myAgent.send(message);
                    mt = MessageTemplate.and(MessageTemplate.MatchConversationId("schedule-set"),
                            MessageTemplate.MatchInReplyTo(message.getReplyWith()));
                    step = 1;
                    break;
                case 1:
                    // Receive a response
                    ACLMessage reply = myAgent.receive(mt);
                    if (reply != null) {
                        if (reply.getPerformative() == ACLMessage.INFORM) {
                            //

                        } else if (reply.getPerformative() == ACLMessage.FAILURE) {
                            // Renvoi une erreur

                        }
                    }
                    else {
                        block();
                    }
                    break;
                case 2:
                    // Confirm the appointment
                    //best proposal consumption - purchase
                    ACLMessage order = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                    //order.addReceiver(bestSeller);
                    order.setContent("content");
                    order.setConversationId("book-trade");
                    order.setReplyWith("order" + System.currentTimeMillis());
                    myAgent.send(order);
                    mt = MessageTemplate.and(MessageTemplate.MatchConversationId("book-trade"),
                            MessageTemplate.MatchInReplyTo(order.getReplyWith()));
                    step = 9;
                    break;

                case 9:
                    // End of
                    System.out.println(getAgent().getLocalName() + " appointment created");
                    break;
            }
        }

        public boolean done() {
            //process terminates here
            return (step == 9);
        }
    }

    private class AppointmentPerformer extends CyclicBehaviour {
        public void action() {

                //proposals only template
                MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.PROPOSE);
                ACLMessage msg = myAgent.receive(mt);
                if (msg != null) {
                    String content = msg.getContent();

                    // Decomposition du message
                    int respondentNumber = 0;

                    /*
                    Envoi la liste de personne et la liste de date a une personne au hasard et le nom de l'emeteur de la
                    reunion et le nombre de personne ayant repondu
                    Modification de la moyenne en fonction de sa liste de date et d'une nombre de personne ayant repondu
                    Supprime son nom de la liste et ajoute 1 au nombre de personne qui on repondu

                    Si la liste est vide renvoi a l'emeteur sinon envoi a une personne au hasard ...
                     */

                    msg.setContent("messageContent");


                    if (respondentNumber>0) {
                        // Passe au suivant
                        ACLMessage order = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                        //order.addReceiver(bestSeller);
                        order.setContent("content");
                        order.setConversationId("book-trade");
                    }
                    else {
                        // Renvoi à l'envoyer
                        ACLMessage order = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                        //order.addReceiver(bestSeller);
                        order.setContent("content");
                        order.setConversationId("book-trade");
                    }
                    myAgent.send(msg);
                }
                else {
                    block();
                }
        }
    }


    private class AppointmentReceive extends CyclicBehaviour {
        public void action() {
            //purchase order as proposal acceptance only template
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL);
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
                String title = msg.getContent();
                ACLMessage reply = msg.createReply();
                //Integer price = (Integer) catalogue.remove(title);
                Hashtable<String, Integer>  infos = new Hashtable<>();//(Hashtable) catalogue.remove(title);
                if (infos != null) {
                    reply.setPerformative(ACLMessage.INFORM);
                    System.out.println(getAID().getLocalName() + ": " + title + " sold to " + msg.getSender().getLocalName());
                }
                else {
                    //title not found in the catalogue, sold to another agent in the meantime (after proposal submission)
                    reply.setPerformative(ACLMessage.FAILURE);
                    reply.setContent("not-available");
                }
                myAgent.send(reply);
            }
            else {
                block();
            }
        }
    }

    /*
     Date format year-month-day-hour
     Generate randomly disponibilities from 2023-01-01-08 to 2023-01-31-17
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


    public Hashtable<String, Integer> dateToHastable(String date){
        // Works with a StringBuilder to be able to "modify" str in getNextNumber

        Hashtable<String, Integer> result = new Hashtable<String, Integer>();
        String temp[] = date.split("-");

        result.put("year", Integer.parseInt(temp[0]));
        result.put("month", Integer.parseInt(temp[1]));
        result.put("day", Integer.parseInt(temp[2]));
        result.put("hour", Integer.parseInt(temp[3]));

        return result;
    }

    /*
    This part convert the message to a hashtable easier to work with.
     */
    public Hashtable<String, Object> contentToHashtable(String content){

        Hashtable<String, Object> result = new Hashtable<String, Object>();
        String temp[] = content.split("£");

        System.out.println(Arrays.toString(temp));

        result.put("meetingCreator", temp[0]);

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
}
