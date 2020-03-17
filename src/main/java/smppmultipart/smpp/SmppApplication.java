package smppmultipart.smpp;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smpp.Data;
import org.smpp.Session;
import org.smpp.TCPIPConnection;
import org.smpp.pdu.Address;
import org.smpp.pdu.BindRequest;
import org.smpp.pdu.BindResponse;
import org.smpp.pdu.BindTransmitter;
import org.smpp.pdu.PDU;
import org.smpp.pdu.SubmitSM;
import org.smpp.pdu.SubmitSMResp;
import org.smpp.pdu.WrongLengthOfStringException;
import org.smpp.util.ByteBuffer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.stereotype.Component;

@SpringBootApplication
public class SmppApplication {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(SmppApplication.class);
	
	private static PDU pdu = null;
    private static Session session = null;

    public static int smsServicePort = 0;
    
    public static String smsServiceHost;
    public static String smsServiceUsername;
    public static String smsServicePassword;
    public static String smsSystemType;
    
    public static String msisdn;

    boolean sessionConnection = false;

    boolean threadActivated = false;

    private String messageEnrichedMessageText = "Voce nao tem saldo suficiente para renovar seu PREZAO 9,99 POR SEMANA. Faca uma recarga agora e garanta a renovacao do seu Prezao.";
    
	public static void main(String[] args) {
		SpringApplication.run(SmppApplication.class, args);
	}
	
    @Bean
    public static PropertySourcesPlaceholderConfigurer placeholderConfigurer() {
        return new PropertySourcesPlaceholderConfigurer();
    }
	
	@Bean
	public CommandLineRunner commandLineRunner(ApplicationContext ctx,
											   @Value("${smsserver.host}") String host,
											   @Value("${smsserver.port}") int port,
											   @Value("${smsserver.username}") String username,
											   @Value("${smsserver.passwd}") String passwd,
											   @Value("${smsserver.systemType}") String systemType,
											   @Value("${smsserver.msisdn}") String number) {

		smsServiceHost = host;
		smsServicePort = port;
		smsServiceUsername = username;
		smsServicePassword = passwd;
		smsSystemType = systemType;
		msisdn = number;
		return args -> {
			
			for(int i = 0; i < 1; i++) {
				SmppApplication smppApplication = new SmppApplication();
				LOGGER.info("[MSISDN]: {}", msisdn);
				smppApplication.sendMessage();

			}
		};
	}

	 private void sendMessage() throws Exception {

	        SubmitSM request = new SubmitSM();
	        request.setDestAddr(createAddress(msisdn));
	        request.setReplaceIfPresentFlag((byte) 0);
	        request.setEsmClass((byte) Data.SM_UDH_GSM);
	        request.setProtocolId((byte) 0);
	        request.setPriorityFlag((byte) 0);
	        request.setRegisteredDelivery((byte) 1);
	        request.setDataCoding((byte) 0);
	        request.setSmDefaultMsgId((byte) 0);

	        SmppApplication.session = getSession(smsServiceHost, smsServicePort, smsServiceUsername, smsServicePassword);
	        
	        SubmitSMResp response = null;

	        String firstPartMessage = messageEnrichedMessageText.substring(0, messageEnrichedMessageText.length()/2);
	        String secondPartMessage = messageEnrichedMessageText.substring(messageEnrichedMessageText.length()/2, messageEnrichedMessageText.length());
	        List<String> messagesParts = new ArrayList<String>();
	        messagesParts.add(firstPartMessage);
	        messagesParts.add(secondPartMessage);

            LOGGER.info("[MULTIPART] - MESSAGE - STATUS: PROCESSING");
        	int currentItem = 0;

	        for(String part : messagesParts) {
	        	
	            ByteBuffer byteBuffer = new ByteBuffer();

	            byteBuffer.appendByte((byte) 5); // UDH Length

	            byteBuffer.appendByte((byte) 0x00); // Indicator for concatenated message

	            byteBuffer.appendByte((byte) 3); // Subheader Length (3 bytes)

	            byteBuffer.appendByte((byte) currentItem) ; //Reference Number

	            byteBuffer.appendByte((byte) messagesParts.size()) ; //Number of pieces

	            byteBuffer.appendByte((byte) (currentItem+1)) ; //Sequence number
	            
	            byteBuffer.appendString(part, Data.ENC_ASCII);
	            
	            request.setShortMessageData(byteBuffer);

	            LOGGER.info("[MULTIPART] - MESSAGE - CURRENT ITEM: {}", currentItem);
	            LOGGER.info("[MULTIPART] - MESSAGE - TOTAL SEGMENTS: {}", messagesParts.size());

	            LOGGER.info("[MULTIPART] - DEBUG-STRING{}", request.debugString());
	            response = SmppApplication.session.submit(request);
	            LOGGER.info("[MULTIPART] - MESSAGE - SENDED: {}", currentItem);
	            currentItem++;

	        }
	        SmppApplication.session.unbind();
	        SmppApplication.session.close();
            LOGGER.info("[MULTIPART] - {}", request.getBody());
	 }

	 private Address createAddress(String messageEnrichedPhoneNumber) throws WrongLengthOfStringException {
		Address address = new Address();
		address.setTon((byte) 1);
		address.setNpi((byte) 1);
		address.setAddress(messageEnrichedPhoneNumber);
		return address;
	 }

	private Session getSession(String smsServiceHost, int smsServicePort, String smsServiceUsername,
							   String smsServicePassword) throws Exception {
		
        LOGGER.info("[CREDENTIALS] - HOST: {}", smsServiceHost);
        LOGGER.info("[CREDENTIALS] - PORT: {}", smsServicePort);
        LOGGER.info("[CREDENTIALS] - USERNAME: {}", smsServiceUsername);
        LOGGER.info("[CREDENTIALS] - PASSWD: {}", smsServicePassword);
        LOGGER.info("[CREDENTIALS] - SYSTEMTYPE: {}", smsSystemType);

		BindRequest request = new BindTransmitter();
		request.setSystemId(smsServiceUsername);
		request.setPassword(smsServicePassword);
		request.setInterfaceVersion((byte) 0x34);
		request.setSystemId(smsSystemType);

		if (!sessionConnection) {
			TCPIPConnection connection = new TCPIPConnection(smsServiceHost, smsServicePort);
            LOGGER.info("[CONNECTION] - TCP/IP OBJECT - STATUS: PREPARED");

			SmppApplication.session = new Session(connection);
			
            LOGGER.info("[CONNECTION] - SESSION OBJECT - STATUS: PREPARED");
		}

        LOGGER.info("[CONNECTION] - STATUS: OPENING");

		BindResponse response = SmppApplication.session.bind(request);

        LOGGER.info("[CONNECTION] - STATUS: CONNECTED");

		if (response.getCommandStatus() == Data.ESME_ROK)
			sessionConnection = true;

		return SmppApplication.session;

	}
}
