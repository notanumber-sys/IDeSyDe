---
layout: default
title: Index
isHome: true
---

Welcome to IDeSyDe's documentation page! Here you can find some quick links to help you navigate the documentation
and also to situate you about the tool (and its methods).

# Quick facts

IDeSyDe is a *generic* tool [for the Design Space Exploration activity]({{ site.project.forsyde }}#our-vision)
within a [Model-Driven Engineering (MDE)](https://www.sciencedirect.com/topics/computer-science/model-driven-engineering)
Process such as [ForSyDe]({{ site.projects.forsyde }}). In particular,
the tool primarily targets *embedded systems* MDE processes, but is not limited to them.


The key underlying concept of IDeSyDe is [Design Space Idenfitication](https://ieeexplore.ieee.org/document/9474082).
This concept give a foundation for separating mathematical formulations and algorithms commonly involved in synthesis and analysis within MDE processes
from the system models it operates on, aside from defining a systematic procedure for extension.
Therefore, to the MDE user, IDeSyDe operates on their *system models*, e.g. [AMALTHEA](https://www.eclipse.org/app4mc/) or 
[ForSyDe IO]({{ site.projects.forsydeio }}).

<!-- IDeSyDe is in fact an implementation of it, and it powers all guarantees and decoupling
seen in the tool itself.  Try [Concepts]({{ site.baseurl }}/concepts)! -->

<!-- ## What is IDeSyDe? -->

As such, IDeSyDe is not a mathematical solver such as [Gurobi](https://www.gurobi.com/) or 
[CPLEX](https://www.ibm.com/analytics/cplex-optimizer), it is not a constraint solver
such as [Gecode](https://www.gecode.org/) or [chuffled](https://github.com/chuffed/chuffed),
nor a general meta-heuristic optimization framework
such as [Opt4j](https://sdarg.github.io/opt4j/) or [JMetal](https://jmetal.github.io/jMetal/).
It uses all these applications and libraries to enrich models that make
better sense to a system designer, such as the mentioned [AMALTHEA](https://www.eclipse.org/app4mc/) or 
[ForSyDe IO]({{ site.projects.forsydeio }}). 

<!-- A quick glance at [Concepts]({{ site.baseurl }}/concepts) can likely clarify this further! -->

## How can I use it?

Short answer: [Install]({{ site.baseurl }}/usage/install) IDeSyDe and follow the [Quick start]({{ site.baseurl }}/usage/quickstart) to get an intuitive grasp!

Long answer:
IDeSyDe being a proof-of-concept prototype, there were decisions that simplified the implementation in order
to prove the concepts quickly. The first one being that the tool that is distributed only accepts [ForSyDe IO]({{ site.projects.forsydeio }})
models (this is not a limtation as [ForSyDe IO]({{ site.projects.forsydeio }}) connects to other MDE frameworks). 
Second, only a handful of MDE "scenarios" (i.e. design spaces) are implemented and distributed with the tool.
They can be found in [Supported DSE]({{ site.baseurl }}/usage/support).
Therefore, direct use of the tool depends on the design spaces distributed with the tool itself. Check [Guidelines]({{ site.baseurl }}/usage/guidelines)
to see how one would extends IDeSyDe or ForSyDe IO to support a design space different than those that exist.

<!-- 
## What can IDeSyDe do for me?

As a tool for Design Space Exploration, IDeSyDe can potentially give you design decisions on your models
such as mappings, schedules, allocations among others. 

These decisions are not random: they respect
design constraints such as memory, time or energy.

This also means that if you let IDeSyDe run long enough, and have just the right amount of computational resources,
it can can give back a solution "*Your design is not possible*".
It is always good to know designs are not possible before they crash! -->

