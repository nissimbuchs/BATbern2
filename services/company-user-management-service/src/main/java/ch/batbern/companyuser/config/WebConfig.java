package ch.batbern.companyuser.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

/**
 * Web MVC Configuration
 * 
 * Configures HTTP message converters to properly handle binary data
 * without applying text encoding (charset) which corrupts images.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        // Find the ByteArrayHttpMessageConverter
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof ByteArrayHttpMessageConverter) {
                ByteArrayHttpMessageConverter byteConverter = (ByteArrayHttpMessageConverter) converter;
                
                // Set supported media types WITHOUT charset
                byteConverter.setSupportedMediaTypes(Arrays.asList(
                    MediaType.APPLICATION_OCTET_STREAM,
                    MediaType.IMAGE_JPEG,
                    MediaType.IMAGE_PNG,
                    MediaType.IMAGE_GIF,
                    new MediaType("image", "svg+xml"),
                    new MediaType("image", "*")
                ));
            }
        }
    }
}
