/*
 * Copyright 2019-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.hateoas.config;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.mvc.TypeConstrainedMappingJackson2HttpMessageConverter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.util.Assert;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Value type to handle registration of hypermedia related {@link HttpMessageConverter}s.
 *
 * @author Oliver Drotbohm
 */
class WebConverters {

	private final List<HypermediaMappingInformation> infos;
	private final ObjectMapper mapper;

	/**
	 * Creates a new {@link WebConverters} from the given {@link ObjectMapper} and {@link HypermediaMappingInformation}s.
	 *
	 * @param mapper must not be {@literal null}.
	 * @param mappingInformation must not be {@literal null}.
	 */
	private WebConverters(ObjectMapper mapper, List<HypermediaMappingInformation> mappingInformation) {

		this.mapper = mapper;
		this.infos = mappingInformation;
	}

	/**
	 * Creates a new {@link WebConverters} from the given {@link ObjectMapper} and {@link HypermediaMappingInformation}s.
	 *
	 * @param mapper must not be {@literal null}.
	 * @param mappingInformations must not be {@literal null}.
	 * @return
	 */
	public static WebConverters of(ObjectMapper mapper, List<HypermediaMappingInformation> mappingInformations) {

		Assert.notNull(mapper, "ObjectMapper must not be null!");
		Assert.notNull(mappingInformations, "Mapping information must not be null!");

		return new WebConverters(mapper, mappingInformations);
	}

	/**
	 * Augments the given {@link List} of {@link HttpMessageConverter}s with the hypermedia enabled ones.
	 *
	 * @param converters must not be {@literal null}.
	 */
	public void augment(List<HttpMessageConverter<?>> converters) {

		Assert.notNull(converters, "HttpMessageConverters must not be null!");

		MappingJackson2HttpMessageConverter converter = converters.stream()
				.filter(MappingJackson2HttpMessageConverter.class::isInstance)
				.map(MappingJackson2HttpMessageConverter.class::cast)
				.findFirst()
				.orElseGet(() -> new MappingJackson2HttpMessageConverter(mapper));

		Stream<MediaType> original = converter.getSupportedMediaTypes().stream();
		Stream<MediaType> custom = infos.stream()
				.flatMap(it -> it.getMediaTypes().stream());

		converter.setSupportedMediaTypes(Stream.concat(custom, original).collect(Collectors.toList()));

		infos.forEach(it -> {

			Class<?> rootType = it.getRootType();
			ObjectMapper objectMapper = it.configureObjectMapper(mapper.copy());
			Map<MediaType, ObjectMapper> mappers = it.getMediaTypes().stream()
					.distinct()
					.collect(Collectors.toMap(Function.identity(), __ -> objectMapper));

			converter.registerObjectMappersForType(rootType, map -> map.putAll(mappers));
		});
	}

	/**
	 * Creates a new {@link TypeConstrainedMappingJackson2HttpMessageConverter} to handle {@link RepresentationModel} for
	 * the given {@link HypermediaMappingInformation} using a copy of the given {@link ObjectMapper}.
	 *
	 * @param type must not be {@literal null}.
	 * @param mapper must not be {@literal null}.
	 * @return
	 */
	private static AbstractJackson2HttpMessageConverter createMessageConverter(HypermediaMappingInformation type,
			ObjectMapper mapper) {

		return new TypeConstrainedMappingJackson2HttpMessageConverter(type.getRootType(), type.getMediaTypes(),
				type.configureObjectMapper(mapper));
	}
}
