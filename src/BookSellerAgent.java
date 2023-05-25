package jadelab2;

import jade.core.Agent;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

import java.util.*;

public class BookSellerAgent extends Agent {
  private Hashtable catalogue;
  private boolean responding = true;
  private BookSellerGui myGui;

  protected void setup() {
    catalogue = new Hashtable();
    myGui = new BookSellerGui(this);
    myGui.display();

    //book selling service registration at DF
    DFAgentDescription dfd = new DFAgentDescription();
    dfd.setName(getAID());
    ServiceDescription sd = new ServiceDescription();
    sd.setType("book-selling");
    sd.setName("JADE-book-trading");
    dfd.addServices(sd);
    try {
      DFService.register(this, dfd);
    }
    catch (FIPAException fe) {
      fe.printStackTrace();
    }
    
    addBehaviour(new OfferRequestsServer());

    addBehaviour(new PurchaseOrdersServer());
  }

  protected void takeDown() {
    //book selling service deregistration at DF
    try {
      DFService.deregister(this);
    }
    catch (FIPAException fe) {
      fe.printStackTrace();
    }
  	myGui.dispose();
    System.out.println("Seller agent " + getAID().getName() + " terminated.");
  }

  //invoked from GUI, when a new book is added to the catalogue
  //public void updateCatalogue(final String title, final int price) {
  public void updateCatalogue(final String title, final Hashtable infos) {
	addBehaviour(new OneShotBehaviour() {
      public void action() {
		//catalogue.put(title, new Integer(price));
		//  ArrayList()
		catalogue.put(title, infos);
		System.out.println(getAID().getLocalName() + ": " + title + " put into the catalogue. Price = " + infos.get("price") + " Shipping = " + infos.get("shipping"));
		printCata();
      }
    } );
  }
  
	private class OfferRequestsServer extends CyclicBehaviour {
	  public void action() {
		  //System.out.println(this.getAgent().getLocalName() + " avant");
		  if(responding){
			  //System.out.println(this.getAgent().getLocalName() + " apres");

			  //proposals only template
			  MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
			  ACLMessage msg = myAgent.receive(mt);
			  if (msg != null) {
				  String title = msg.getContent();
				  ACLMessage reply = msg.createReply();

				  Hashtable infos = (Hashtable) catalogue.get(title);

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
		  } else {
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
			  Hashtable<String, Integer>  infos = (Hashtable) catalogue.remove(title);
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
				printCata();
			}
			else {
			  block();
			}
		}
	  }

	public void updateResponse(boolean b){
		  this.responding = b;
		  System.out.println(this.getLocalName() + " response = " + b);
	}

	public Hashtable convert(String s){
		Hashtable ht = new Hashtable();

		s = s.substring(1, s.length() -1);

		String[] pairs = s.split(",");

		for (String pair : pairs) {
			String[] keyValue = pair.split("=");

			ht.put(keyValue[0].trim(), keyValue[1].trim());
		}

		return ht;
	}

	public void printCata(){
		System.out.println(this.getLocalName() + " : " + this.catalogue);
	}

}
