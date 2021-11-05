/*******************************************************************************
 * Copyright (c) 2021 Obeo.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Obeo - initial API and implementation
 *******************************************************************************/
package org.eclipse.sirius.web.services.representations;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.eclipse.sirius.web.core.api.IEditingContext;
import org.eclipse.sirius.web.persistence.repositories.IRepresentationRepository;
import org.eclipse.sirius.web.representations.IRepresentationMetadata;
import org.eclipse.sirius.web.spring.collaborative.api.IRepresentationMetadataSearchService;
import org.springframework.stereotype.Service;

/**
 * Used to retrieve representation metadata.
 *
 * @author pcdavid
 */
@Service
public class RepresentationMetadataSearchService implements IRepresentationMetadataSearchService {
    private final IRepresentationRepository representationRepository;

    private final ObjectMapper objectMapper;

    public RepresentationMetadataSearchService(IRepresentationRepository representationRepository, ObjectMapper objectMapper) {
        this.representationRepository = Objects.requireNonNull(representationRepository);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Override
    public Optional<IRepresentationMetadata> findById(IEditingContext editingContext, UUID representationId) {
        return this.representationRepository.findById(representationId).map(new RepresentationMapper(this.objectMapper)::toRepresentationMetadataDTO);
    }

}
