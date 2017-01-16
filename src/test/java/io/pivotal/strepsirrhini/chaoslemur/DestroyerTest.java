/*
 * Copyright 2014-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.pivotal.strepsirrhini.chaoslemur;

import io.pivotal.strepsirrhini.chaoslemur.infrastructure.DestructionException;
import io.pivotal.strepsirrhini.chaoslemur.infrastructure.Infrastructure;
import io.pivotal.strepsirrhini.chaoslemur.reporter.Reporter;
import io.pivotal.strepsirrhini.chaoslemur.state.State;
import io.pivotal.strepsirrhini.chaoslemur.state.StateProvider;
import io.pivotal.strepsirrhini.chaoslemur.task.Task;
import io.pivotal.strepsirrhini.chaoslemur.task.TaskRepository;
import io.pivotal.strepsirrhini.chaoslemur.task.TaskUriBuilder;
import io.pivotal.strepsirrhini.chaoslemur.task.Trigger;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

public final class DestroyerTest {

    private final ExecutorService executorService = mock(ExecutorService.class);

    private final FateEngine fateEngine = mock(FateEngine.class);

    @SuppressWarnings("unchecked")
    private final Future<Void> future = mock(Future.class);

    private final Infrastructure infrastructure = mock(Infrastructure.class);

    private final Member member1 = new Member("test-id-1", "test-deployment", "test-job", "test-name-1");

    private final Member member2 = new Member("test-id-2", "test-deployment", "test-job", "test-name-2");

    private final Set<Member> members = Stream.of(this.member1, this.member2).collect(Collectors.toSet());

    private final Reporter reporter = mock(Reporter.class);

    private final ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);

    private final StateProvider stateProvider = mock(StateProvider.class);

    private final TaskRepository taskRepository = mock(TaskRepository.class);

    private final TaskUriBuilder taskUriBuilder = mock(TaskUriBuilder.class);

    private final Destroyer destroyer = new Destroyer(false, this.executorService, this.fateEngine, this.infrastructure, this.reporter, this.stateProvider, "", this.taskRepository,
        this.taskUriBuilder);

    private final MockMvc mockMvc = standaloneSetup(this.destroyer).build();

    @Test
    public void destroy() throws DestructionException {
        this.destroyer.destroy();

        runRunnables();

        verify(this.infrastructure).destroy(this.member1);
        verify(this.infrastructure, never()).destroy(this.member2);
    }

    @Test
    public void destroyDryRun() throws DestructionException {
        Destroyer destroyer = new Destroyer(true, this.executorService, this.fateEngine, this.infrastructure,
            this.reporter, this.stateProvider, "", this.taskRepository, this.taskUriBuilder);

        destroyer.destroy();
        runRunnables();

        verify(this.infrastructure, never()).destroy(this.member1);
        verify(this.infrastructure, never()).destroy(this.member2);
    }

    @Test
    public void destroyError() throws DestructionException, IllegalStateException {
        doThrow(new IllegalStateException()).when(this.infrastructure).destroy(this.member1);

        this.destroyer.destroy();

        verify(this.infrastructure, never()).destroy(this.member1);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void destroyFail() throws DestructionException {
        doThrow(new DestructionException()).when(this.infrastructure).destroy(this.member1);

        this.destroyer.destroy();

        verify(this.infrastructure, never()).destroy(this.member1);
    }

    @Test
    public void destroyWhenStopped() throws DestructionException {
        when(this.stateProvider.get()).thenReturn(State.STOPPED);

        this.destroyer.destroy();

        verify(this.infrastructure, never()).getMembers();
    }

    @Before
    @SuppressWarnings("unchecked")
    public void executor() throws ExecutionException, InterruptedException {
        when((Future<Void>) this.executorService.submit(any(Runnable.class))).thenReturn(this.future);
        when(this.future.get()).thenReturn(null);
    }

    @Test
    public void invalidKey() throws Exception {
        this.mockMvc.perform(post("/chaos")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"foo\":\"destroy\"}"))
            .andExpect(status().isBadRequest());

        verify(this.infrastructure, never()).destroy(this.member1);
    }

    @Test
    public void invalidValue() throws Exception {
        this.mockMvc.perform(post("/chaos")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"event\":\"foo\"}"))
            .andExpect(status().isBadRequest());

        verify(this.infrastructure, never()).destroy(this.member1);
    }

    @Test
    public void manualDestroy() throws Exception {
        Task task = new Task(2L, Trigger.MANUAL);
        when(this.taskRepository.create(Trigger.MANUAL)).thenReturn(task);
        when(this.taskUriBuilder.getUri(task)).thenReturn(URI.create("http://foo.com"));

        this.mockMvc.perform(post("/chaos")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"event\":\"destroy\"}"))
            .andExpect(status().isAccepted())
            .andExpect(header().string("Location", "http://foo.com"));

        runRunnables();

        verify(this.infrastructure).destroy(this.member1);
        verify(this.infrastructure, never()).destroy(this.member2);
    }

    @Before
    public void members() {
        when(this.infrastructure.getMembers()).thenReturn(this.members);
        when(this.fateEngine.shouldDie(this.member1)).thenReturn(true);
        when(this.fateEngine.shouldDie(this.member2)).thenReturn(false);
        when(this.taskRepository.create(Trigger.SCHEDULED)).thenReturn(new Task(1L, Trigger.SCHEDULED));
    }

    private void runRunnables() {
        verify(this.executorService, atMost(1)).execute(this.runnableCaptor.capture());
        this.runnableCaptor.getAllValues().forEach(Runnable::run);

        verify(this.executorService, times(2)).submit(this.runnableCaptor.capture());
        this.runnableCaptor.getAllValues().forEach(Runnable::run);
    }

}
