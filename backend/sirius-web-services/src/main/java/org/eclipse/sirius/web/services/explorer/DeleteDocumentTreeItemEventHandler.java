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
package org.eclipse.sirius.web.services.explorer;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.edit.domain.AdapterFactoryEditingDomain;
import org.eclipse.sirius.web.core.api.ErrorPayload;
import org.eclipse.sirius.web.core.api.IEditingContext;
import org.eclipse.sirius.web.core.api.IInput;
import org.eclipse.sirius.web.core.api.IPayload;
import org.eclipse.sirius.web.emf.services.EditingContext;
import org.eclipse.sirius.web.services.api.document.Document;
import org.eclipse.sirius.web.services.api.document.IDocumentService;
import org.eclipse.sirius.web.services.messages.IServicesMessageService;
import org.eclipse.sirius.web.spring.collaborative.api.ChangeDescription;
import org.eclipse.sirius.web.spring.collaborative.api.ChangeKind;
import org.eclipse.sirius.web.spring.collaborative.api.IEditingContextEventHandler;
import org.eclipse.sirius.web.spring.collaborative.api.Monitoring;
import org.eclipse.sirius.web.spring.collaborative.trees.dto.DeleteTreeItemInput;
import org.eclipse.sirius.web.spring.collaborative.trees.dto.DeleteTreeItemSuccessPayload;
import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import reactor.core.publisher.Sinks.Many;
import reactor.core.publisher.Sinks.One;

/**
 * Handles document deletion triggered via a tree item from the explorer.
 *
 * @author pcdavid
 */
@Service
public class DeleteDocumentTreeItemEventHandler implements IEditingContextEventHandler {

    public static final String DOCUMENT_ITEM_KIND = "Document"; //$NON-NLS-1$

    private final IDocumentService documentService;

    private final IServicesMessageService messageService;

    private final Counter counter;

    public DeleteDocumentTreeItemEventHandler(IDocumentService documentService, IServicesMessageService messageService, MeterRegistry meterRegistry) {
        this.documentService = Objects.requireNonNull(documentService);
        this.messageService = Objects.requireNonNull(messageService);

        // @formatter:off
        this.counter = Counter.builder(Monitoring.EVENT_HANDLER)
                .tag(Monitoring.NAME, this.getClass().getSimpleName())
                .register(meterRegistry);
        // @formatter:on
    }

    @Override
    public boolean canHandle(IEditingContext editingContext, IInput input) {
        return input instanceof DeleteTreeItemInput && DOCUMENT_ITEM_KIND.equals(((DeleteTreeItemInput) input).getKind());
    }

    @Override
    public void handle(One<IPayload> payloadSink, Many<ChangeDescription> changeDescriptionSink, IEditingContext editingContext, IInput input) {
        this.counter.increment();

        IPayload payload = new ErrorPayload(input.getId(), this.messageService.unexpectedError());
        ChangeDescription changeDescription = new ChangeDescription(ChangeKind.NOTHING, editingContext.getId(), input);

     // @formatter:off
        var optionalEditingDomain = Optional.of(editingContext)
                .filter(EditingContext.class::isInstance)
                .map(EditingContext.class::cast)
                .map(EditingContext::getDomain);
        // @formatter:on

        if (input instanceof DeleteTreeItemInput) {
            DeleteTreeItemInput deleteDocumentInput = (DeleteTreeItemInput) input;
            var optionalDocument = this.documentService.getDocument(deleteDocumentInput.getTreeItemId());

            if (optionalEditingDomain.isPresent() && optionalDocument.isPresent()) {
                AdapterFactoryEditingDomain editingDomain = optionalEditingDomain.get();
                Document document = optionalDocument.get();

                ResourceSet resourceSet = editingDomain.getResourceSet();
                URI uri = URI.createURI(document.getId().toString());

                // @formatter:off
                List<Resource> resourcesToDelete = resourceSet.getResources().stream()
                        .filter(resource -> resource.getURI().equals(uri))
                        .collect(Collectors.toUnmodifiableList());
                resourcesToDelete.stream().forEach(resourceSet.getResources()::remove);
                // @formatter:on

                this.documentService.delete(document.getId());

                payload = new DeleteTreeItemSuccessPayload(input.getId());
                changeDescription = new ChangeDescription(ChangeKind.SEMANTIC_CHANGE, editingContext.getId(), input);
                payloadSink.tryEmitValue(payload);
                changeDescriptionSink.tryEmitNext(changeDescription);
            }
        }

        payloadSink.tryEmitValue(payload);
        changeDescriptionSink.tryEmitNext(changeDescription);
    }
}
