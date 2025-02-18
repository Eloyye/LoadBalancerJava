package repository;

import pods.BackendPod;

import java.time.ZonedDateTime;
import java.util.List;

public record BackendPodEventContext(BackendPodEvent event,
                                     ZonedDateTime timestamp,
                                     List<BackendPod> affectedPods) {}
