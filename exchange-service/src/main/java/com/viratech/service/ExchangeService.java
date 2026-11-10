package com.viratech.service;

import com.viratech.environment.InstanceInformationService;
import com.viratech.model.Exchange;
import com.viratech.repository.ExchangeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class ExchangeService{

    @Autowired
    ExchangeRepository repository;

    @Autowired
    InstanceInformationService informationService;

    public Exchange find(BigDecimal amount, String from, String to ){

        String port = informationService.retrieveServerPort();
        String host = informationService.retrieveInstanceInfo();

        Exchange exchange = repository.findByFromAndTo(from, to);

        if(exchange == null) throw new RuntimeException("Currency Unsupported");

        BigDecimal conversionFactor = exchange.getConversionFactor();
        BigDecimal convertedValue = conversionFactor.multiply(amount);
        exchange.setConvertedValue(convertedValue);
        exchange.setEnvironment(host + " VERSION: kube-v1 " + " PORT " + port);

        return exchange;
    }
}
