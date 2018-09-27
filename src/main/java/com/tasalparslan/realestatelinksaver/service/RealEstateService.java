package com.tasalparslan.realestatelinksaver.service;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.tasalparslan.realestatelinksaver.client.RealEstateClient;
import com.tasalparslan.realestatelinksaver.model.RealEstate;
import com.tasalparslan.realestatelinksaver.repository.RealEstateRepository;

@Service
public class RealEstateService {

	private static final Logger logger = LoggerFactory.getLogger(RealEstateService.class);

	private static final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/YYYY HH:mm:ss", new Locale("tr"));

	@Autowired
	private RealEstateClient estateClient;

	@Autowired
	private JavaMailSender sender;

	@Autowired
	private RealEstateRepository repository;

	@Value("${mail.to}")
	private String mailTo;

	@Value("${hrefs.link}")
	private String hrefsLink;

	// @Scheduled(fixedRate = 240000) runs every 4 minutes
	@Scheduled(cron = "${cron.pattern}")
	public void updateAndNotify() {
		List<RealEstate> previousDayEstateData = getPreviousDayEstateData();
		List<RealEstate> todayEstateData = getTodayEstateData();

		HashMap<String, List<RealEstate>> differentiationResult = giveDifferencesBetweenGivenListById(previousDayEstateData, todayEstateData);
		if (differentiationResult.get("addedEstates").size() == 0 && differentiationResult.get("removedEstates").size() == 0) {
			logger.info("Nothing change.. ");
		}
		else {
			cleanOldDbData();
			addTodaysData(todayEstateData);
		}

		sendEmail(differentiationResult);
		logger.info("Finished stuff.. Waiting for next call... " + dateFormat.format(new Date()));
	}

	public List<RealEstate> getPreviousDayEstateData() {
		logger.info("Getting previous day data... " + dateFormat.format(new Date()));

		List<RealEstate> previousDayData = repository.findAll();
		System.out.println("getRealEstateData size: " + previousDayData.size());

		return previousDayData;
	}

	public List<RealEstate> getTodayEstateData() {
		logger.info("Getting today's data... " + dateFormat.format(new Date()));

		List<RealEstate> todaysData = estateClient.getCurrentDayData();
		return todaysData;

	}

	public void addTodaysData(List<RealEstate> todayEstateData) {
		logger.info("Saving today's data.." + dateFormat.format(new Date()));
		repository.saveAll(todayEstateData);
	}

	public void cleanOldDbData() {
		logger.info("Deleting old data.." + dateFormat.format(new Date()));
		repository.deleteAll();
	}

	public HashMap<String, List<RealEstate>> giveDifferencesBetweenGivenListById(List<RealEstate> previousData, List<RealEstate> actualData) {
		logger.info("Comparing previous data with actual data... " + dateFormat.format(new Date()));

		List<RealEstate> removedEstates = previousData.stream().filter(o1 -> actualData.stream().noneMatch(o2 -> o2.getId().equals(o1.getId()))).collect(Collectors.toList());
		logger.info("Removed Data Count: " + removedEstates.size());

		List<RealEstate> addedEstates = actualData.stream().filter(o1 -> previousData.stream().noneMatch(o2 -> o2.getId().equals(o1.getId()))).collect(Collectors.toList());
		logger.info("Added Data Count: " + addedEstates.size());

		HashMap<String, List<RealEstate>> resp = new HashMap<>();
		resp.put("removedEstates", removedEstates);
		resp.put("addedEstates", addedEstates);

		return resp;
	}

	public void sendEmail(HashMap<String, List<RealEstate>> differentiationResult) {
		logger.info("Sending email to " + mailTo + "... " + dateFormat.format(new Date()));

		try {

			MimeMessage message = sender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, true);

			helper.setTo(mailTo);

			helper.setSubject(dateFormat.format(new Date()) + " Data");

			String content = "";

			if (differentiationResult.get("addedEstates").size() == 0 && differentiationResult.get("removedEstates").size() == 0) {
				logger.info("Nothing change.. ");
				content = "<html><body><h2>" + dateFormat.format(new Date()) + " Data</h2><br/><h3>Sorry, Nothing Changed :( </h3>";
			}
			else {

				StringBuilder added = new StringBuilder();
				for (RealEstate rs : differentiationResult.get("addedEstates")) {
					added.append("<a href=" + hrefsLink + ">" + rs.getId() + "</a><br/>");
				}

				StringBuilder removed = new StringBuilder();
				for (RealEstate rs : differentiationResult.get("removedEstates")) {
					removed.append("<a href=" + hrefsLink + ">" + rs.getId() + "</a><br/>");
				}

				content = "<html><body><h2>" + dateFormat.format(new Date()) + " Data</h2><br/><h3>Added Data</h3>" + added.toString() + "<br/><br/><h3>Removed Data</h3>" + removed.toString() + "<body></html>";
			}

			helper.setText(content, true);

			sender.send(message);
			logger.info("Sent email...");
		}
		catch (MessagingException e) {
			logger.error(e.getMessage());
		}
	}

}
