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

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.edit.domain.AdapterFactoryEditingDomain;
import org.eclipse.sirius.web.core.api.ErrorPayload;
import org.eclipse.sirius.web.core.api.IEditingContext;
import org.eclipse.sirius.web.core.api.IInput;
import org.eclipse.sirius.web.core.api.IPayload;
import org.eclipse.sirius.web.emf.services.EditingContext;
import org.eclipse.sirius.web.services.api.document.Document;
import org.eclipse.sirius.web.services.api.document.IDocumentService;
import org.eclipse.sirius.web.services.documents.DocumentMetadataAdapter;
import org.eclipse.sirius.web.services.messages.IServicesMessageService;
import org.eclipse.sirius.web.spring.collaborative.api.ChangeDescription;
import org.eclipse.sirius.web.spring.collaborative.api.ChangeKind;
import org.eclipse.sirius.web.spring.collaborative.api.IEditingContextEventHandler;
import org.eclipse.sirius.web.spring.collaborative.api.Monitoring;
import org.eclipse.sirius.web.spring.collaborative.trees.dto.RenameTreeItemInput;
import org.eclipse.sirius.web.spring.collaborative.trees.dto.RenameTreeItemSuccessPayload;
import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import reactor.core.publisher.Sinks.Many;
import reactor.core.publisher.Sinks.One;

/**
 * Handles document renaming triggered via a tree item from the explorer.
 *
 * @author pcdavid
 */
@Service
public class RenameDocumentTreeItemEventHandler implements IEditingContextEventHandler {

    private static final String DOCUMENT_ITEM_KIND = "Document"; //$NON-NLS-1$

    private final IDocumentService documentService;

    private final IServicesMessageService messageService;

    private final Counter counter;

    public RenameDocumentTreeItemEventHandler(IDocumentService documentService, IServicesMessageService messageService, MeterRegistry meterRegistry) {
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
        return input instanceof RenameTreeItemInput && DOCUMENT_ITEM_KIND.equals(((RenameTreeItemInput) input).getKind());
    }

    @Override
    public void handle(One<IPayload> payloadSink, Many<ChangeDescription> changeDescriptionSink, IEditingContext editingContext, IInput input) {
        this.counter.increment();

        ChangeDescription changeDescription = new ChangeDescription(ChangeKind.NOTHING, editingContext.getId(), input);
        String message = this.messageService.invalidInput(input.getClass().getSimpleName(), RenameTreeItemInput.class.getSimpleName());
        IPayload payload = new ErrorPayload(input.getId(), message);

        // @formatter:off
        Optional<AdapterFactoryEditingDomain> optionalEditingDomain = Optional.of(editingContext)
                .filter(EditingContext.class::isInstance)
                .map(EditingContext.class::cast)
                .map(EditingContext::getDomain);
        // @formatter:on

        if (input instanceof RenameTreeItemInput) {
            RenameTreeItemInput renameDocumentInput = (RenameTreeItemInput) input;
            UUID documentId = renameDocumentInput.getTreeItemId();
            String newName = renameDocumentInput.getNewName();

            Optional<Document> optionalDocument = this.documentService.rename(documentId, newName);
            if (optionalEditingDomain.isPresent() && optionalDocument.isPresent()) {
                Document document = optionalDocument.get();
                AdapterFactoryEditingDomain adapterFactoryEditingDomain = optionalEditingDomain.get();
                ResourceSet resourceSet = adapterFactoryEditingDomain.getResourceSet();

                // @formatter:off
                resourceSet.getResources().stream()
                        .filter(resource -> document.getId().equals(UUID.fromString(resource.getURI().toString())))
                        .findFirst()
                        .ifPresent(resource -> {
                            resource.eAdapters().stream()
                                .filter(DocumentMetadataAdapter.class::isInstance)
                                .map(DocumentMetadataAdapter.class::cast)
                                .findFirst()
                                .ifPresent(adapter -> adapter.setName(newName));
                        });
                // @formatter:on

                changeDescription = new ChangeDescription(ChangeKind.SEMANTIC_CHANGE, editingContext.getId(), input);
                payload = new RenameTreeItemSuccessPayload(input.getId());
            }
        }
        payloadSink.tryEmitValue(payload);
        changeDescriptionSink.tryEmitNext(changeDescription);
    }

}
