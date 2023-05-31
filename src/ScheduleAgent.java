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

import java.util.Hashtable;

public class ScheduleAgent extends Agent {

    protected void setup() {
        //hashtable = new Hashtable(); // /!\ Idée de code

        //book selling service registration at DF
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("schedule-noidea");
        sd.setName("JADE-schedule-trading");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        }
        catch (FIPAException fe) {
            fe.printStackTrace();
        }

        addBehaviour(new ScheduleAgent.OfferRequestsServer());
        addBehaviour(new ScheduleAgent.PurchaseOrdersServer());
        addBehaviour(new ScheduleAgent.RequestPerformer());
    }

    protected void takeDown() {
        //book selling service deregistration at DF
        try {
            DFService.deregister(this);
        }
        catch (FIPAException fe) {
            fe.printStackTrace();
        }
        System.out.println("Seller agent " + getAID().getName() + " terminated.");
    }


    private class OfferRequestsServer extends CyclicBehaviour {
        public void action() {

                //proposals only template
                MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
                ACLMessage msg = myAgent.receive(mt);
                if (msg != null) {
                    String title = msg.getContent();
                    ACLMessage reply = msg.createReply();

                    Hashtable infos = new Hashtable(); //(Hashtable) catalogue.get(title);

                    if (infos!=null) {
                        //title found in the catalogue, respond with its price as a proposal
                        reply.setPerformative(ACLMessage.PROPOSE);
                        reply.setContent(infos.toString());
                    }
                    else {
                        //title not found in the catalogue
                        reply.setPerformative(ACLMessage.REFUSE);
                        reply.setContent("not-available");
                    }
                    myAgent.send(reply);
                }
                else {
                    block();
                }
        }
    }


    private class PurchaseOrdersServer extends CyclicBehaviour {
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

    private class RequestPerformer extends Behaviour {
        private AID bestSeller;
        private int bestPrice;
        private int repliesCnt = 0;
        private MessageTemplate mt;
        private int step = 0;

        private long startingTime;
        private long currentTime;
        private long timeout = 2000;

        public void action() {
            switch (step) {
                case 0:
                    //call for proposal (CFP) to found sellers
                    ACLMessage cfp = new ACLMessage(ACLMessage.CFP);

                    cfp.setContent("content");
                    cfp.setConversationId("book-trade");
                    cfp.setReplyWith("cfp"+System.currentTimeMillis()); //unique value
                    myAgent.send(cfp);
                    mt = MessageTemplate.and(MessageTemplate.MatchConversationId("book-trade"),
                            MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
                    step = 1;
                    startingTime = System.currentTimeMillis();
                    break;
                case 1:
                    //collect proposals
                    currentTime = System.currentTimeMillis();
                    if (currentTime-startingTime>timeout) step = 9;
                    ACLMessage reply = myAgent.receive(mt);
                    if (reply != null) {
                        if (reply.getPerformative() == ACLMessage.PROPOSE) {
                            //proposal received
                            //int price = Integer.parseInt(reply.getContent());
                            Hashtable infos = new Hashtable();//convert(reply.getContent());
                            int price = Integer.parseInt(infos.get("price").toString());
                            int shipping = Integer.parseInt(infos.get("shipping").toString());

                            //Hashtable infos = (Hashtable) reply.getContent();
                            if (bestSeller == null || price + shipping < bestPrice) {
                                //the best proposal as for now
                                bestPrice = price + shipping;
                                bestSeller = reply.getSender();
                            }
                        }
                        if(true) {
                            step = 2;
                        } else {
                            step = 4;
                        }
                    }
                    else {
                        block(timeout);
                    }
                    break;
                case 2:
                    if(repliesCnt > 0) {
                        //best proposal consumption - purchase
                        ACLMessage order = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                        order.addReceiver(bestSeller);
                        order.setContent("content");
                        order.setConversationId("book-trade");
                        order.setReplyWith("order" + System.currentTimeMillis());
                        myAgent.send(order);
                        mt = MessageTemplate.and(MessageTemplate.MatchConversationId("book-trade"),
                                MessageTemplate.MatchInReplyTo(order.getReplyWith()));
                        step = 3;
                    } else {
                        step = 9;
                    }
                    break;
                case 3:
                    //seller confirms the transaction
                    reply = myAgent.receive(mt);
                    if (reply != null) {
                        if (reply.getPerformative() == ACLMessage.INFORM) {
                            //purchase succeeded
                            System.out.println(getAID().getLocalName() + ": " + "targetBookTitle" + " purchased for " + bestPrice + " from " + reply.getSender().getLocalName());

                            System.out.println(getAID().getLocalName() + ": waiting for the next purchase order.");
                            //myAgent.doDelete();
                        }
                        else {
                            System.out.println(getAID().getLocalName() + ": purchase has failed. " + "targetBookTitle" + " was sold in the meantime.");
                        }
                        step = 4;	//this state ends the purchase process
                    }
                    else {
                        block();
                    }
                    break;
                case 9:
                    // Seller don't reply
                    System.out.println(getAgent().getLocalName() + " sellers doesn't response");
                    //"targetBookTitle" = "";
                    step = 4;
                    break;
            }
        }

        public boolean done() {
            if (step == 2 && bestSeller == null) {
                System.out.println(getAID().getLocalName() + ": " + "targetBookTitle" + " is not on sale.");
            }
            //process terminates here if purchase has failed (title not on sale) or book was successfully bought
            return ((step == 2 && bestSeller == null) || step == 4);
        }
    }

    private float average(float actualAverage, float myValue, int respondentNumber) {
        return ((actualAverage * respondentNumber) + myValue)/(respondentNumber+1);
    }

}
