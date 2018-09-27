package com.tasalparslan.realestatelinksaver.client;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.text.StringEscapeUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.tasalparslan.realestatelinksaver.model.RealEstate;

@Component
public class RealEstateClient {

	private static final Logger logger = LoggerFactory.getLogger(RealEstateClient.class);

	@Value("${data.link}")
	private String dataLink;

	int maxPageIndex = 1;

	public List<RealEstate> getCurrentDayData() {

		RestTemplate restTemplate = new RestTemplate();
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		List<RealEstate> realEstates = new ArrayList<>();

		int pageIndex = 1;
		realEstates.addAll(getRealEstateDataByPageIndex(restTemplate, headers, pageIndex));
		if (maxPageIndex > 1) {
			for (int i = 2; i < maxPageIndex + 1; i++) {
				realEstates.addAll(getRealEstateDataByPageIndex(restTemplate, headers, i));
			}
		}
		logger.info("Total Fetched Data Count: " + realEstates.size());
		return realEstates;
	}

	private List<RealEstate> getRealEstateDataByPageIndex(RestTemplate restTemplate, HttpHeaders headers, int pageIndex) {

		String requestJson = "{\"pageIndex\":" + pageIndex + ",\"categoryID\":null,\"cityID\":null,\"minAppraisedPrice\":null,\"maxAppraisedPrice\":null,\"latitude\":\"\",\"longitude\":\"\",\"sorting\":null}";

		HttpEntity<String> entity = new HttpEntity<String>(requestJson, headers);

		String response = restTemplate.postForObject(dataLink, entity, String.class);
		List<RealEstate> realEstates = new ArrayList<>();

		if (pageIndex == 1) {
			maxPageIndex = getMaxPageIndex(response);
		}
		return parseRealEstateElement(realEstates, response);

	}

	public List<RealEstate> parseRealEstateElement(List<RealEstate> realEstates, String esateData) {

		// \\u converting to < > char
		String unescapedResponse = StringEscapeUtils.unescapeJava(esateData);

		// parsing html data.. removing unuasable things
		String htmlAsString = unescapedResponse.split("\"Data\":\"")[1].split("\",\"Exception\"")[0];

		// mapping html to java object
		Document document = Jsoup.parse(htmlAsString);

		Elements records = document.getElementsByClass("faqItem");

		for (Element element : records) {
			RealEstate rs = new RealEstate();
			rs.setAddress(element.getElementsByClass("map-adres").text());
			rs.setDescription(element.getElementsByClass("summary").text());
			rs.setPrice(element.getElementsByClass("price").text());
			rs.setId(element.getElementsByClass("offerButton").attr("href").split("id=")[1]);

			realEstates.add(rs);
		}
		return realEstates;
	}

	private int getMaxPageIndex(String response) {
		
		String unescapedResponse = StringEscapeUtils.unescapeJava(response);
		String htmlAsString = unescapedResponse.split("\"Data\":\"")[1].split("\",\"Exception\"")[0];
		Document document = Jsoup.parse(htmlAsString);
		Element pagerContent = document.getElementById("ctl00_pgrEstatesBottom");

		String maxPageIndex = pagerContent.getElementsByClass("pager-last").attr("data-page");
		return Integer.parseInt(maxPageIndex);
	}

}
