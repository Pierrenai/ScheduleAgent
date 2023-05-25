package jadelab2;

import jade.core.AID;

import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Hashtable;
import javax.swing.*;

class BookSellerGui extends JFrame {	
	private BookSellerAgent myAgent;
	
	private JTextField titleField, priceField, shippingField;
	
	BookSellerGui(BookSellerAgent a) {
		super(a.getLocalName());
		
		myAgent = a;
		
		JPanel p = new JPanel();
		p.setLayout(new GridLayout(4, 2));
		p.add(new JLabel("Title:"));
		titleField = new JTextField(15);
		p.add(titleField);
		p.add(new JLabel("Price:"));
		priceField = new JTextField(15);
		p.add(priceField);
		p.add(new JLabel("Shipping:"));
		shippingField = new JTextField(15);
		p.add(shippingField);

		JCheckBox checkBox1 = new JCheckBox("Response", true);
		checkBox1.setBounds(100,100, 50,50);
		p.add(checkBox1);

		getContentPane().add(p, BorderLayout.CENTER);
		
		JButton addButton = new JButton("Add");
		addButton.addActionListener( new ActionListener() {
			public void actionPerformed(ActionEvent ev) {
				try {
					String title = titleField.getText().trim();
					String price = priceField.getText().trim();
					String shipping = shippingField.getText().trim();
					//myAgent.updateCatalogue(title, Integer.parseInt(price) + Integer.parseInt(shipping));
					Hashtable<String, Integer>  infos = new Hashtable();
					infos.put("price", Integer.parseInt(price));
					infos.put("shipping", Integer.parseInt(shipping));

					/* Print infos
					System.out.println("infos: " + infos.toString());

					String sinfos = infos.toString();
					String[] sinfosdec = sinfos.split(",");
					System.out.println("sinfosdec: " + sinfosdec.toString());
					*/

					myAgent.updateCatalogue(title, infos);
					titleField.setText("");
					priceField.setText("");
					shippingField.setText("");
				}
				catch (Exception e) {
					JOptionPane.showMessageDialog(BookSellerGui.this, "Invalid values. " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE); 
				}
			}
		} );

		checkBox1.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent actionEvent) {
				if(checkBox1.isSelected()) myAgent.updateResponse(true);
				else myAgent.updateResponse(false);
			}
		});
		p = new JPanel();
		p.add(addButton);
		getContentPane().add(p, BorderLayout.SOUTH);
		
		addWindowListener(new	WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				myAgent.doDelete();
			}
		} );
		
		setResizable(false);
	}
	
	public void display() {
		pack();
		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		int centerX = (int)screenSize.getWidth() / 2;
		int centerY = (int)screenSize.getHeight() / 2;
		setLocation(centerX - getWidth() / 2, centerY - getHeight() / 2);
		setVisible(true);
	}	
}
