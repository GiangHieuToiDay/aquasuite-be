package com.aquasuite.ims.module.fulfillment.internal.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import java.nio.file.Path;

@Configuration(proxyBeanMethods = false)
public class UnavailableDeliveryPhotoStorage {
    @Bean
    @ConditionalOnMissingBean(DeliveryPhotoStorage.class)
    DeliveryPhotoStorage deliveryPhotoStorage(
            @Value("${aquasuite.delivery.photo-directory:./uploads/delivery-photos}") String directory) {
        return new LocalDeliveryPhotoStorage(Path.of(directory));
    }
}
