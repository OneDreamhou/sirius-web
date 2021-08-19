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

import org.eclipse.sirius.web.core.api.ErrorPayload;
import org.eclipse.sirius.web.core.api.IEditService;
import org.eclipse.sirius.web.core.api.IEditingContext;
import org.eclipse.sirius.web.core.api.IInput;
import org.eclipse.sirius.web.core.api.IObjectService;
import org.eclipse.sirius.web.spring.collaborative.api.ChangeDescription;
import org.eclipse.sirius.web.spring.collaborative.api.ChangeKind;
import org.eclipse.sirius.web.spring.collaborative.api.EventHandlerResponse;
import org.eclipse.sirius.web.spring.collaborative.api.IEditingContextEventHandler;
import org.eclipse.sirius.web.spring.collaborative.api.Monitoring;
import org.eclipse.sirius.web.spring.collaborative.messages.ICollaborativeMessageService;
import org.eclipse.sirius.web.spring.collaborative.trees.dto.DeleteTreeItemInput;
import org.eclipse.sirius.web.spring.collaborative.trees.dto.DeleteTreeItemSuccessPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Handles semantic object deletion triggered via a tree item from the explorer.
 *
 * @author pcdavid
 */
@Service
public class DeleteObjectTreeItemEventHandler implements IEditingContextEventHandler {

    private final Logger logger = LoggerFactory.getLogger(DeleteObjectTreeItemEventHandler.class);

    private final IObjectService objectService;

    private final IEditService editService;

    private final ICollaborativeMessageService messageService;

    private final Counter counter;

    public DeleteObjectTreeItemEventHandler(IObjectService objectService, IEditService editService, ICollaborativeMessageService messageService, MeterRegistry meterRegistry) {
        this.objectService = Objects.requireNonNull(objectService);
        this.editService = Objects.requireNonNull(editService);
        this.messageService = Objects.requireNonNull(messageService);

        // @formatter:off
        this.counter = Counter.builder(Monitoring.EVENT_HANDLER)
                .tag(Monitoring.NAME, this.getClass().getSimpleName())
                .register(meterRegistry);
        // @formatter:on
    }

    @Override
    public boolean canHandle(IInput input) {
        return input instanceof DeleteTreeItemInput && this.isDomainObjectKind(((DeleteTreeItemInput) input).getKind());
    }

    private boolean isDomainObjectKind(String kind) {
        // TODO There should really a better way to do this...
        return kind != null && kind.contains("::"); //$NON-NLS-1$
    }

    @Override
    public EventHandlerResponse handle(IEditingContext editingContext, IInput input) {
        this.counter.increment();

        if (input instanceof DeleteTreeItemInput) {
            DeleteTreeItemInput deleteObjectInput = (DeleteTreeItemInput) input;

            Optional<Object> optionalObject = this.objectService.getObject(editingContext, deleteObjectInput.getTreeItemId().toString());
            if (optionalObject.isPresent()) {
                Object object = optionalObject.get();
                this.editService.delete(object);

                return new EventHandlerResponse(new ChangeDescription(ChangeKind.SEMANTIC_CHANGE, editingContext.getId()), new DeleteTreeItemSuccessPayload(input.getId()));
            } else {
                this.logger.warn("The object with the id {} does not exist", deleteObjectInput.getTreeItemId()); //$NON-NLS-1$
            }
        }

        String message = this.messageService.invalidInput(input.getClass().getSimpleName(), DeleteTreeItemInput.class.getSimpleName());
        return new EventHandlerResponse(new ChangeDescription(ChangeKind.NOTHING, editingContext.getId()), new ErrorPayload(input.getId(), message));
    }
}
