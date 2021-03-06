/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.event.impl.jobs.queues;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.sling.commons.scheduler.Scheduler;
import org.apache.sling.event.impl.EnvironmentComponent;
import org.apache.sling.event.impl.jobs.JobEvent;
import org.apache.sling.event.impl.jobs.config.InternalQueueConfiguration;
import org.apache.sling.event.jobs.JobUtil;

/**
 * This queue acts similar to the parallel job queue. Except that
 * new jobs are selected based on a round robin topic selection scheme.
 * Failing jobs are rescheduled and put at the end of the queue.
 */
public final class TopicRoundRobinJobQueue extends AbstractParallelJobQueue {

    /** The topic set. */
    private final List<String> topics = new ArrayList<String>();

    /** The topic map. */
    private final Map<String, List<JobEvent>> topicMap = new HashMap<String, List<JobEvent>>();

    /** Topic index. */
    private int topicIndex;

    /** Event count. */
    private int eventCount;

    private boolean isWaitingForNext = false;

    public TopicRoundRobinJobQueue(final String name,
                           final InternalQueueConfiguration config,
                           final EnvironmentComponent env,
                           final Scheduler scheduler) {
        super(name, config, env, scheduler);
    }

    @Override
    public String getStateInfo() {
        return super.getStateInfo() + ", eventCount=" + this.eventCount + ", isWaitingForNext=" + this.isWaitingForNext;
    }

    @Override
    protected boolean canBeMarkedForRemoval() {
        boolean result = super.canBeMarkedForRemoval();
        if ( result ) {
            result = !this.isWaitingForNext;
        }
        return result;
    }

    @Override
    protected void put(final JobEvent event) {
        // is this a close?
        if ( event.event == null ) {
            return;
        }
        final String topic = (String)event.event.getProperty(JobUtil.PROPERTY_JOB_TOPIC);
        synchronized ( this.topicMap ) {
            List<JobEvent> events = this.topicMap.get(topic);
            if ( events == null ) {
                events = new LinkedList<JobEvent>();
                this.topicMap.put(topic, events);
                this.topics.add(topic);
            }
            events.add(event);
            this.eventCount++;
            if ( this.isWaitingForNext ) {
                this.isWaitingForNext = false;
                // wake up take()
                this.topicMap.notify();
            }
        }
    }

    @Override
    protected JobEvent take() {
        JobEvent e = null;
        synchronized ( this.topicMap ) {
            if ( this.eventCount == 0 ) {
                // wait for a new event
                this.isWaitingForNext = true;
                while ( this.isWaitingForNext ) {
                    try {
                        this.topicMap.wait();
                    } catch (final InterruptedException ie) {
                        this.ignoreException(ie);
                    }
                }
            }
            if ( this.eventCount > 0 ) {
                while ( e == null ) {
                    final String topic = this.topics.get(this.topicIndex);
                    final List<JobEvent> events = this.topicMap.get(topic);
                    if ( events.size() > 0 ) {
                        e = events.remove(0);
                    }
                    this.topicIndex++;
                    if ( this.topicIndex == this.topics.size() ) {
                        this.topicIndex = 0;
                    }
                }
                this.eventCount--;
            }
        }
        return e;
    }

    @Override
    protected boolean isEmpty() {
        synchronized ( this.topicMap ) {
            return this.eventCount == 0;
        }
    }

    /**
     * @see org.apache.sling.event.jobs.Queue#clear()
     */
    public void clear() {
        synchronized ( this.topicMap ) {
            this.eventCount = 0;
            this.topics.clear();
            this.topicMap.clear();
        }
        super.clear();
    }

    @Override
    protected Collection<JobEvent> removeAllJobs() {
        final List<JobEvent> events = new ArrayList<JobEvent>();
        synchronized ( this.topicMap ) {
            for(final List<JobEvent> l : this.topicMap.values() ) {
                events.addAll(l);
            }
            this.eventCount = 0;
            this.topics.clear();
            this.topicMap.clear();
        }
        return events;
    }
}

