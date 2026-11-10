package com.viratech.service;

import com.viratech.dto.ExchangeRequest;
import com.viratech.environment.InstanceInformationService;
import com.viratech.model.Book;
import com.viratech.proxy.ExchangeProxy;
import com.viratech.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;

@Service
@RequiredArgsConstructor
public class BookService {

    private final BookRepository repository;
    private final InstanceInformationService informationService;
    private final ExchangeProxy proxy;

    private Logger logger = LoggerFactory.getLogger(BookService.class.getSimpleName());

    public Book findBook(Long id, String currency){

        Book book = repository.findById(id)
                .orElseThrow(()-> new RuntimeException("Book not found"));

        logger.info("Calculating the converted price of the book from {} USD to {}.",
                book.getPrice(), currency);

        HashMap<String, String> params = new HashMap<>();
        params.put("amount", book.getPrice().toString());
        params.put("from", "USD");
        params.put("to", currency);

        ExchangeRequest response = proxy.getExchange(book.getPrice(), "USD", currency);

        String port =  informationService.retrieveServerPort();
        String host =  informationService.retrieveInstanceInfo();

        book.setEnvironment(
                "BOOK HOST " + host +
                " BOOK PORT: " + port +
                " VERSION: kube-v1" +
                " | " +"EXCHANGE HOST: " +
                response.environment());
        book.setPrice(response.convertedValue());
        book.setCurrency(currency);

        return book;
    }
}
