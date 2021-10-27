/*******************************************************************************
 * Copyright (c) 2019, 2021 Obeo.
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
package org.eclipse.sirius.web.graphql.datafetchers.mutation;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.eclipse.sirius.web.annotations.graphql.GraphQLMutationTypes;
import org.eclipse.sirius.web.annotations.spring.graphql.MutationDataFetcher;
import org.eclipse.sirius.web.core.api.ErrorPayload;
import org.eclipse.sirius.web.core.api.IPayload;
import org.eclipse.sirius.web.graphql.messages.IGraphQLMessageService;
import org.eclipse.sirius.web.graphql.schema.MutationTypeProvider;
import org.eclipse.sirius.web.spring.collaborative.api.IEditingContextEventProcessorRegistry;
import org.eclipse.sirius.web.spring.collaborative.dto.RenameRepresentationInput;
import org.eclipse.sirius.web.spring.collaborative.dto.RenameRepresentationSuccessPayload;
import org.eclipse.sirius.web.spring.collaborative.trees.dto.RenameTreeItemInput;
import org.eclipse.sirius.web.spring.collaborative.trees.dto.RenameTreeItemSuccessPayload;
import org.eclipse.sirius.web.spring.graphql.api.IDataFetcherWithFieldCoordinates;

import graphql.schema.DataFetchingEnvironment;

/**
 * The data fetcher used to rename a tree item.
 * <p>
 * It will be used to handle the following GraphQL field:
 * </p>
 *
 * <pre>
 * type Mutation {
 *   renameTreeItem(input : RenameTreeItemInput!): RenameTreeItemPayload!
 * }
 * </pre>
 *
 * @author fbarbin
 * @author pcdavid
 */
// @formatter:off
@GraphQLMutationTypes(
    input = RenameTreeItemInput.class,
    payloads = {
        RenameTreeItemSuccessPayload.class
    }
)
@MutationDataFetcher(type = MutationTypeProvider.TYPE, field = MutationRenameTreeItemDataFetcher.RENAME_TREE_ITEM_FIELD)
// @formatter:on
public class MutationRenameTreeItemDataFetcher implements IDataFetcherWithFieldCoordinates<CompletableFuture<IPayload>> {

    public static final String RENAME_TREE_ITEM_FIELD = "renameTreeItem"; //$NON-NLS-1$

    private final ObjectMapper objectMapper;

    private final IEditingContextEventProcessorRegistry editingContextEventProcessorRegistry;

    private final IGraphQLMessageService messageService;

    public MutationRenameTreeItemDataFetcher(ObjectMapper objectMapper, IEditingContextEventProcessorRegistry editingContextEventProcessorRegistry, IGraphQLMessageService messageService) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.editingContextEventProcessorRegistry = Objects.requireNonNull(editingContextEventProcessorRegistry);
        this.messageService = Objects.requireNonNull(messageService);
    }

    @Override
    public CompletableFuture<IPayload> get(DataFetchingEnvironment environment) throws Exception {
        Object argument = environment.getArgument(MutationTypeProvider.INPUT_ARGUMENT);
        var input = this.objectMapper.convertValue(argument, RenameTreeItemInput.class);

        String kind = input.getKind();
        boolean isRepresentationRename = !this.isDomainObjectKind(kind) && !Objects.equals(kind, "Model"); //$NON-NLS-1$
        if (isRepresentationRename) {
            var renameRepresentationInput = new RenameRepresentationInput(input.getId(), input.getEditingContextId(), input.getTreeItemId(), input.getNewName());
            // @formatter:off
            return this.editingContextEventProcessorRegistry.dispatchEvent(renameRepresentationInput.getEditingContextId(), renameRepresentationInput)
                       .map((IPayload payload) -> {
                           if (payload instanceof RenameRepresentationSuccessPayload) {
                               return new RenameTreeItemSuccessPayload(((RenameRepresentationSuccessPayload) payload).getId());
                           } else {
                               return payload;
                           }
                       })
                       .defaultIfEmpty(new ErrorPayload(input.getId(), this.messageService.unexpectedError()))
                       .toFuture();
            // @formatter:on
        } else {
            // @formatter:off
            return this.editingContextEventProcessorRegistry.dispatchEvent(input.getEditingContextId(), input)
                       .defaultIfEmpty(new ErrorPayload(input.getId(), this.messageService.unexpectedError()))
                       .toFuture();
            // @formatter:on
        }
    }

    private boolean isDomainObjectKind(String kind) {
        // TODO There should really a better way to do this...
        return kind != null && kind.contains("::"); //$NON-NLS-1$
    }
}
