/*
 * Copyright 2002-2013 the original author or authors.
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

package org.springframework.beans.factory.annotation;

import java.io.Serializable;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AutowireCandidateQualifier;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.Order;
import org.springframework.tests.sample.beans.ITestBean;
import org.springframework.tests.sample.beans.IndexedTestBean;
import org.springframework.tests.sample.beans.NestedTestBean;
import org.springframework.tests.sample.beans.TestBean;
import org.springframework.util.SerializationTestUtils;

import static org.junit.Assert.*;

/**
 * @author Juergen Hoeller
 * @author Mark Fisher
 * @author Sam Brannen
 * @author Chris Beams
 */
public class AutowiredAnnotationBeanPostProcessorTests {

	@Test
	public void testIncompleteBeanDefinition() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		bf.registerBeanDefinition("testBean", new GenericBeanDefinition());
		try {
			bf.getBean("testBean");
		}
		catch (BeanCreationException ex) {
			assertTrue(ex.getRootCause() instanceof IllegalStateException);
		}
	}

	@Test
	public void testResourceInjection() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		RootBeanDefinition bd = new RootBeanDefinition(ResourceInjectionBean.class);
		bd.setScope(RootBeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", bd);
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);

		ResourceInjectionBean bean = (ResourceInjectionBean) bf.getBean("annotatedBean");
		assertSame(tb, bean.getTestBean());
		assertSame(tb, bean.getTestBean2());

		bean = (ResourceInjectionBean) bf.getBean("annotatedBean");
		assertSame(tb, bean.getTestBean());
		assertSame(tb, bean.getTestBean2());
	}

	@Test
	public void testExtendedResourceInjection() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		bf.registerResolvableDependency(BeanFactory.class, bf);
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		RootBeanDefinition bd = new RootBeanDefinition(TypedExtendedResourceInjectionBean.class);
		bd.setScope(RootBeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", bd);
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);
		NestedTestBean ntb = new NestedTestBean();
		bf.registerSingleton("nestedTestBean", ntb);

		TypedExtendedResourceInjectionBean bean = (TypedExtendedResourceInjectionBean) bf.getBean("annotatedBean");
		assertSame(tb, bean.getTestBean());
		assertSame(tb, bean.getTestBean2());
		assertSame(tb, bean.getTestBean3());
		assertSame(tb, bean.getTestBean4());
		assertSame(ntb, bean.getNestedTestBean());
		assertSame(bf, bean.getBeanFactory());

		bean = (TypedExtendedResourceInjectionBean) bf.getBean("annotatedBean");
		assertSame(tb, bean.getTestBean());
		assertSame(tb, bean.getTestBean2());
		assertSame(tb, bean.getTestBean3());
		assertSame(tb, bean.getTestBean4());
		assertSame(ntb, bean.getNestedTestBean());
		assertSame(bf, bean.getBeanFactory());

		String[] depBeans = bf.getDependenciesForBean("annotatedBean");
		assertEquals(2, depBeans.length);
		assertEquals("testBean", depBeans[0]);
		assertEquals("nestedTestBean", depBeans[1]);
	}

	@Test
	public void testExtendedResourceInjectionWithOverriding() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		bf.registerResolvableDependency(BeanFactory.class, bf);
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		RootBeanDefinition annotatedBd = new RootBeanDefinition(TypedExtendedResourceInjectionBean.class);
		TestBean tb2 = new TestBean();
		annotatedBd.getPropertyValues().add("testBean2", tb2);
		bf.registerBeanDefinition("annotatedBean", annotatedBd);
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);
		NestedTestBean ntb = new NestedTestBean();
		bf.registerSingleton("nestedTestBean", ntb);

		TypedExtendedResourceInjectionBean bean = (TypedExtendedResourceInjectionBean) bf.getBean("annotatedBean");
		assertSame(tb, bean.getTestBean());
		assertSame(tb2, bean.getTestBean2());
		assertSame(tb, bean.getTestBean3());
		assertSame(tb, bean.getTestBean4());
		assertSame(ntb, bean.getNestedTestBean());
		assertSame(bf, bean.getBeanFactory());
		bf.destroySingletons();
	}

	@Test
	public void testExtendedResourceInjectionWithSkippedOverriddenMethods() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		bf.registerResolvableDependency(BeanFactory.class, bf);
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		RootBeanDefinition annotatedBd = new RootBeanDefinition(OverriddenExtendedResourceInjectionBean.class);
		bf.registerBeanDefinition("annotatedBean", annotatedBd);
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);
		NestedTestBean ntb = new NestedTestBean();
		bf.registerSingleton("nestedTestBean", ntb);

		OverriddenExtendedResourceInjectionBean bean = (OverriddenExtendedResourceInjectionBean) bf.getBean("annotatedBean");
		assertSame(tb, bean.getTestBean());
		assertNull(bean.getTestBean2());
		assertSame(tb, bean.getTestBean3());
		assertSame(tb, bean.getTestBean4());
		assertSame(ntb, bean.getNestedTestBean());
		assertNull(bean.getBeanFactory());
		assertTrue(bean.baseInjected);
		assertTrue(bean.subInjected);
		bf.destroySingletons();
	}

	@Test
	public void testExtendedResourceInjectionWithAtRequired() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		bf.registerResolvableDependency(BeanFactory.class, bf);
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		bf.addBeanPostProcessor(new RequiredAnnotationBeanPostProcessor());
		RootBeanDefinition bd = new RootBeanDefinition(TypedExtendedResourceInjectionBean.class);
		bd.setScope(RootBeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", bd);
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);
		NestedTestBean ntb = new NestedTestBean();
		bf.registerSingleton("nestedTestBean", ntb);

		TypedExtendedResourceInjectionBean bean = (TypedExtendedResourceInjectionBean) bf.getBean("annotatedBean");
		assertSame(tb, bean.getTestBean());
		assertSame(tb, bean.getTestBean2());
		assertSame(tb, bean.getTestBean3());
		assertSame(tb, bean.getTestBean4());
		assertSame(ntb, bean.getNestedTestBean());
		assertSame(bf, bean.getBeanFactory());
	}

	@Test
	public void testOptionalResourceInjection() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(OptionalResourceInjectionBean.class));
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);
		IndexedTestBean itb = new IndexedTestBean();
		bf.registerSingleton("indexedTestBean", itb);
		NestedTestBean ntb1 = new NestedTestBean();
		bf.registerSingleton("nestedTestBean1", ntb1);
		NestedTestBean ntb2 = new NestedTestBean();
		bf.registerSingleton("nestedTestBean2", ntb2);

		OptionalResourceInjectionBean bean = (OptionalResourceInjectionBean) bf.getBean("annotatedBean");
		assertSame(tb, bean.getTestBean());
		assertSame(tb, bean.getTestBean2());
		assertSame(tb, bean.getTestBean3());
		assertSame(tb, bean.getTestBean4());
		assertSame(itb, bean.getIndexedTestBean());
		assertEquals(2, bean.getNestedTestBeans().length);
		assertSame(ntb1, bean.getNestedTestBeans()[0]);
		assertSame(ntb2, bean.getNestedTestBeans()[1]);
		assertEquals(2, bean.nestedTestBeansField.length);
		assertSame(ntb1, bean.nestedTestBeansField[0]);
		assertSame(ntb2, bean.nestedTestBeansField[1]);
		bf.destroySingletons();
	}

	@Test
	public void testOptionalCollectionResourceInjection() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		RootBeanDefinition rbd = new RootBeanDefinition(OptionalCollectionResourceInjectionBean.class);
		rbd.setScope(RootBeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", rbd);
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);
		IndexedTestBean itb = new IndexedTestBean();
		bf.registerSingleton("indexedTestBean", itb);
		NestedTestBean ntb1 = new NestedTestBean();
		bf.registerSingleton("nestedTestBean1", ntb1);
		NestedTestBean ntb2 = new NestedTestBean();
		bf.registerSingleton("nestedTestBean2", ntb2);

		// Two calls to verify that caching doesn't break re-creation.
		OptionalCollectionResourceInjectionBean bean = (OptionalCollectionResourceInjectionBean) bf.getBean("annotatedBean");
		bean = (OptionalCollectionResourceInjectionBean) bf.getBean("annotatedBean");
		assertSame(tb, bean.getTestBean());
		assertSame(tb, bean.getTestBean2());
		assertSame(tb, bean.getTestBean3());
		assertSame(tb, bean.getTestBean4());
		assertSame(itb, bean.getIndexedTestBean());
		assertEquals(2, bean.getNestedTestBeans().size());
		assertSame(ntb1, bean.getNestedTestBeans().get(0));
		assertSame(ntb2, bean.getNestedTestBeans().get(1));
		assertEquals(2, bean.nestedTestBeansSetter.size());
		assertSame(ntb1, bean.nestedTestBeansSetter.get(0));
		assertSame(ntb2, bean.nestedTestBeansSetter.get(1));
		assertEquals(2, bean.nestedTestBeansField.size());
		assertSame(ntb1, bean.nestedTestBeansField.get(0));
		assertSame(ntb2, bean.nestedTestBeansField.get(1));
		bf.destroySingletons();
	}

	@Test
	public void testOptionalCollectionResourceInjectionWithSingleElement() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		RootBeanDefinition rbd = new RootBeanDefinition(OptionalCollectionResourceInjectionBean.class);
		rbd.setScope(RootBeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", rbd);
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);
		IndexedTestBean itb = new IndexedTestBean();
		bf.registerSingleton("indexedTestBean", itb);
		NestedTestBean ntb1 = new NestedTestBean();
		bf.registerSingleton("nestedTestBean1", ntb1);

		// Two calls to verify that caching doesn't break re-creation.
		OptionalCollectionResourceInjectionBean bean = (OptionalCollectionResourceInjectionBean) bf.getBean("annotatedBean");
		bean = (OptionalCollectionResourceInjectionBean) bf.getBean("annotatedBean");
		assertSame(tb, bean.getTestBean());
		assertSame(tb, bean.getTestBean2());
		assertSame(tb, bean.getTestBean3());
		assertSame(tb, bean.getTestBean4());
		assertSame(itb, bean.getIndexedTestBean());
		assertEquals(1, bean.getNestedTestBeans().size());
		assertSame(ntb1, bean.getNestedTestBeans().get(0));
		assertEquals(1, bean.nestedTestBeansSetter.size());
		assertSame(ntb1, bean.nestedTestBeansSetter.get(0));
		assertEquals(1, bean.nestedTestBeansField.size());
		assertSame(ntb1, bean.nestedTestBeansField.get(0));
		bf.destroySingletons();
	}

	@Test
	public void testOptionalResourceInjectionWithIncompleteDependencies() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(OptionalResourceInjectionBean.class));
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);

		OptionalResourceInjectionBean bean = (OptionalResourceInjectionBean) bf.getBean("annotatedBean");
		assertSame(tb, bean.getTestBean());
		assertSame(tb, bean.getTestBean2());
		assertSame(tb, bean.getTestBean3());
		assertNull(bean.getTestBean4());
		assertNull(bean.getNestedTestBeans());
		bf.destroySingletons();
	}

	@Test
	public void testOptionalResourceInjectionWithNoDependencies() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(OptionalResourceInjectionBean.class));

		OptionalResourceInjectionBean bean = (OptionalResourceInjectionBean) bf.getBean("annotatedBean");
		assertNull(bean.getTestBean());
		assertNull(bean.getTestBean2());
		assertNull(bean.getTestBean3());
		assertNull(bean.getTestBean4());
		assertNull(bean.getNestedTestBeans());
		bf.destroySingletons();
	}

	@Test
	public void testOrderedResourceInjection() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		bf.setDependencyComparator(AnnotationAwareOrderComparator.INSTANCE);
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(OptionalResourceInjectionBean.class));
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);
		IndexedTestBean itb = new IndexedTestBean();
		bf.registerSingleton("indexedTestBean", itb);
		OrderedNestedTestBean ntb1 = new OrderedNestedTestBean();
		ntb1.setOrder(2);
		bf.registerSingleton("nestedTestBean1", ntb1);
		OrderedNestedTestBean ntb2 = new OrderedNestedTestBean();
		ntb2.setOrder(1);
		bf.registerSingleton("nestedTestBean2", ntb2);

		OptionalResourceInjectionBean bean = (OptionalResourceInjectionBean) bf.getBean("annotatedBean");
		assertSame(tb, bean.getTestBean());
		assertSame(tb, bean.getTestBean2());
		assertSame(tb, bean.getTestBean3());
		assertSame(tb, bean.getTestBean4());
		assertSame(itb, bean.getIndexedTestBean());
		assertEquals(2, bean.getNestedTestBeans().length);
		assertSame(ntb2, bean.getNestedTestBeans()[0]);
		assertSame(ntb1, bean.getNestedTestBeans()[1]);
		assertEquals(2, bean.nestedTestBeansField.length);
		assertSame(ntb2, bean.nestedTestBeansField[0]);
		assertSame(ntb1, bean.nestedTestBeansField[1]);
		bf.destroySingletons();
	}

	@Test
	public void testAnnotationOrderedResourceInjection() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		bf.setDependencyComparator(AnnotationAwareOrderComparator.INSTANCE);
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(OptionalResourceInjectionBean.class));
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);
		IndexedTestBean itb = new IndexedTestBean();
		bf.registerSingleton("indexedTestBean", itb);
		FixedOrder2NestedTestBean ntb1 = new FixedOrder2NestedTestBean();
		bf.registerSingleton("nestedTestBean1", ntb1);
		FixedOrder1NestedTestBean ntb2 = new FixedOrder1NestedTestBean();
		bf.registerSingleton("nestedTestBean2", ntb2);

		OptionalResourceInjectionBean bean = (OptionalResourceInjectionBean) bf.getBean("annotatedBean");
		assertSame(tb, bean.getTestBean());
		assertSame(tb, bean.getTestBean2());
		assertSame(tb, bean.getTestBean3());
		assertSame(tb, bean.getTestBean4());
		assertSame(itb, bean.getIndexedTestBean());
		assertEquals(2, bean.getNestedTestBeans().length);
		assertSame(ntb2, bean.getNestedTestBeans()[0]);
		assertSame(ntb1, bean.getNestedTestBeans()[1]);
		assertEquals(2, bean.nestedTestBeansField.length);
		assertSame(ntb2, bean.nestedTestBeansField[0]);
		assertSame(ntb1, bean.nestedTestBeansField[1]);
		bf.destroySingletons();
	}

	@Test
	public void testOrderedCollectionResourceInjection() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		bf.setDependencyComparator(AnnotationAwareOrderComparator.INSTANCE);
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		RootBeanDefinition rbd = new RootBeanDefinition(OptionalCollectionResourceInjectionBean.class);
		rbd.setScope(RootBeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", rbd);
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);
		IndexedTestBean itb = new IndexedTestBean();
		bf.registerSingleton("indexedTestBean", itb);
		OrderedNestedTestBean ntb1 = new OrderedNestedTestBean();
		ntb1.setOrder(2);
		bf.registerSingleton("nestedTestBean1", ntb1);
		OrderedNestedTestBean ntb2 = new OrderedNestedTestBean();
		ntb2.setOrder(1);
		bf.registerSingleton("nestedTestBean2", ntb2);

		// Two calls to verify that caching doesn't break re-creation.
		OptionalCollectionResourceInjectionBean bean = (OptionalCollectionResourceInjectionBean) bf.getBean("annotatedBean");
		bean = (OptionalCollectionResourceInjectionBean) bf.getBean("annotatedBean");
		assertSame(tb, bean.getTestBean());
		assertSame(tb, bean.getTestBean2());
		assertSame(tb, bean.getTestBean3());
		assertSame(tb, bean.getTestBean4());
		assertSame(itb, bean.getIndexedTestBean());
		assertEquals(2, bean.getNestedTestBeans().size());
		assertSame(ntb2, bean.getNestedTestBeans().get(0));
		assertSame(ntb1, bean.getNestedTestBeans().get(1));
		assertEquals(2, bean.nestedTestBeansSetter.size());
		assertSame(ntb2, bean.nestedTestBeansSetter.get(0));
		assertSame(ntb1, bean.nestedTestBeansSetter.get(1));
		assertEquals(2, bean.nestedTestBeansField.size());
		assertSame(ntb2, bean.nestedTestBeansField.get(0));
		assertSame(ntb1, bean.nestedTestBeansField.get(1));
		bf.destroySingletons();
	}

	@Test
	public void testAnnotationOrderedCollectionResourceInjection() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		bf.setDependencyComparator(AnnotationAwareOrderComparator.INSTANCE);
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		RootBeanDefinition rbd = new RootBeanDefinition(OptionalCollectionResourceInjectionBean.class);
		rbd.setScope(RootBeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", rbd);
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);
		IndexedTestBean itb = new IndexedTestBean();
		bf.registerSingleton("indexedTestBean", itb);
		FixedOrder2NestedTestBean ntb1 = new FixedOrder2NestedTestBean();
		bf.registerSingleton("nestedTestBean1", ntb1);
		FixedOrder1NestedTestBean ntb2 = new FixedOrder1NestedTestBean();
		bf.registerSingleton("nestedTestBean2", ntb2);

		// Two calls to verify that caching doesn't break re-creation.
		OptionalCollectionResourceInjectionBean bean = (OptionalCollectionResourceInjectionBean) bf.getBean("annotatedBean");
		bean = (OptionalCollectionResourceInjectionBean) bf.getBean("annotatedBean");
		assertSame(tb, bean.getTestBean());
		assertSame(tb, bean.getTestBean2());
		assertSame(tb, bean.getTestBean3());
		assertSame(tb, bean.getTestBean4());
		assertSame(itb, bean.getIndexedTestBean());
		assertEquals(2, bean.getNestedTestBeans().size());
		assertSame(ntb2, bean.getNestedTestBeans().get(0));
		assertSame(ntb1, bean.getNestedTestBeans().get(1));
		assertEquals(2, bean.nestedTestBeansSetter.size());
		assertSame(ntb2, bean.nestedTestBeansSetter.get(0));
		assertSame(ntb1, bean.nestedTestBeansSetter.get(1));
		assertEquals(2, bean.nestedTestBeansField.size());
		assertSame(ntb2, bean.nestedTestBeansField.get(0));
		assertSame(ntb1, bean.nestedTestBeansField.get(1));
		bf.destroySingletons();
	}

	@Test
	public void testConstructorResourceInjection() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		bf.registerResolvableDependency(BeanFactory.class, bf);
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		RootBeanDefinition bd = new RootBeanDefinition(ConstructorResourceInjectionBean.class);
		bd.setScope(RootBeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", bd);
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);
		NestedTestBean ntb = new NestedTestBean();
		bf.registerSingleton("nestedTestBean", ntb);

		ConstructorResourceInjectionBean bean = (ConstructorResourceInjectionBean) bf.getBean("annotatedBean");
		assertSame(tb, bean.getTestBean());
		assertSame(tb, bean.getTestBean2());
		assertSame(tb, bean.getTestBean3());
		assertSame(tb, bean.getTestBean4());
		assertSame(ntb, bean.getNestedTestBean());
		assertSame(bf, bean.getBeanFactory());

		bean = (ConstructorResourceInjectionBean) bf.getBean("annotatedBean");
		assertSame(tb, bean.getTestBean());
		assertSame(tb, bean.getTestBean2());
		assertSame(tb, bean.getTestBean3());
		assertSame(tb, bean.getTestBean4());
		assertSame(ntb, bean.getNestedTestBean());
		assertSame(bf, bean.getBeanFactory());
	}

	@Test
	public void testConstructorResourceInjectionWithMultipleCandidates() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(ConstructorsResourceInjectionBean.class));
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);
		NestedTestBean ntb1 = new NestedTestBean();
		bf.registerSingleton("nestedTestBean1", ntb1);
		NestedTestBean ntb2 = new NestedTestBean();
		bf.registerSingleton("nestedTestBean2", ntb2);

		ConstructorsResourceInjectionBean bean = (ConstructorsResourceInjectionBean) bf.getBean("annotatedBean");
		assertNull(bean.getTestBean3());
		assertSame(tb, bean.getTestBean4());
		assertEquals(2, bean.getNestedTestBeans().length);
		assertSame(ntb1, bean.getNestedTestBeans()[0]);
		assertSame(ntb2, bean.getNestedTestBeans()[1]);
		bf.destroySingletons();
	}

	@Test
	public void testConstructorResourceInjectionWithMultipleCandidatesAsCollection() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(
				ConstructorsCollectionResourceInjectionBean.class));
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);
		NestedTestBean ntb1 = new NestedTestBean();
		bf.registerSingleton("nestedTestBean1", ntb1);
		NestedTestBean ntb2 = new NestedTestBean();
		bf.registerSingleton("nestedTestBean2", ntb2);

		ConstructorsCollectionResourceInjectionBean bean = (ConstructorsCollectionResourceInjectionBean) bf.getBean("annotatedBean");
		assertNull(bean.getTestBean3());
		assertSame(tb, bean.getTestBean4());
		assertEquals(2, bean.getNestedTestBeans().size());
		assertSame(ntb1, bean.getNestedTestBeans().get(0));
		assertSame(ntb2, bean.getNestedTestBeans().get(1));
		bf.destroySingletons();
	}

	@Test
	public void testConstructorResourceInjectionWithMultipleOrderedCandidates() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		bf.setDependencyComparator(AnnotationAwareOrderComparator.INSTANCE);
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(ConstructorsResourceInjectionBean.class));
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);
		FixedOrder2NestedTestBean ntb1 = new FixedOrder2NestedTestBean();
		bf.registerSingleton("nestedTestBean1", ntb1);
		FixedOrder1NestedTestBean ntb2 = new FixedOrder1NestedTestBean();
		bf.registerSingleton("nestedTestBean2", ntb2);

		ConstructorsResourceInjectionBean bean = (ConstructorsResourceInjectionBean) bf.getBean("annotatedBean");
		assertNull(bean.getTestBean3());
		assertSame(tb, bean.getTestBean4());
		assertEquals(2, bean.getNestedTestBeans().length);
		assertSame(ntb2, bean.getNestedTestBeans()[0]);
		assertSame(ntb1, bean.getNestedTestBeans()[1]);
		bf.destroySingletons();
	}

	@Test
	public void testConstructorResourceInjectionWithMultipleCandidatesAsOrderedCollection() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		bf.setDependencyComparator(AnnotationAwareOrderComparator.INSTANCE);
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(
				ConstructorsCollectionResourceInjectionBean.class));
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);
		FixedOrder2NestedTestBean ntb1 = new FixedOrder2NestedTestBean();
		bf.registerSingleton("nestedTestBean1", ntb1);
		FixedOrder1NestedTestBean ntb2 = new FixedOrder1NestedTestBean();
		bf.registerSingleton("nestedTestBean2", ntb2);

		ConstructorsCollectionResourceInjectionBean bean = (ConstructorsCollectionResourceInjectionBean) bf.getBean("annotatedBean");
		assertNull(bean.getTestBean3());
		assertSame(tb, bean.getTestBean4());
		assertEquals(2, bean.getNestedTestBeans().size());
		assertSame(ntb2, bean.getNestedTestBeans().get(0));
		assertSame(ntb1, bean.getNestedTestBeans().get(1));
		bf.destroySingletons();
	}

	@Test
	public void testConstructorResourceInjectionWithMultipleCandidatesAndFallback() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(ConstructorsResourceInjectionBean.class));
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);

		ConstructorsResourceInjectionBean bean = (ConstructorsResourceInjectionBean) bf.getBean("annotatedBean");
		assertSame(tb, bean.getTestBean3());
		assertNull(bean.getTestBean4());
		bf.destroySingletons();
	}

	@Test
	public void testConstructorResourceInjectionWithMultipleCandidatesAndDefaultFallback() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(ConstructorsResourceInjectionBean.class));

		ConstructorsResourceInjectionBean bean = (ConstructorsResourceInjectionBean) bf.getBean("annotatedBean");
		assertNull(bean.getTestBean3());
		assertNull(bean.getTestBean4());
		bf.destroySingletons();
	}

	@Test
	public void testConstructorInjectionWithMap() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		RootBeanDefinition bd = new RootBeanDefinition(MapConstructorInjectionBean.class);
		bd.setScope(RootBeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", bd);
		TestBean tb1 = new TestBean();
		TestBean tb2 = new TestBean();
		bf.registerSingleton("testBean1", tb1);
		bf.registerSingleton("testBean2", tb1);

		MapConstructorInjectionBean bean = (MapConstructorInjectionBean) bf.getBean("annotatedBean");
		assertEquals(2, bean.getTestBeanMap().size());
		assertTrue(bean.getTestBeanMap().keySet().contains("testBean1"));
		assertTrue(bean.getTestBeanMap().keySet().contains("testBean2"));
		assertTrue(bean.getTestBeanMap().values().contains(tb1));
		assertTrue(bean.getTestBeanMap().values().contains(tb2));

		bean = (MapConstructorInjectionBean) bf.getBean("annotatedBean");
		assertEquals(2, bean.getTestBeanMap().size());
		assertTrue(bean.getTestBeanMap().keySet().contains("testBean1"));
		assertTrue(bean.getTestBeanMap().keySet().contains("testBean2"));
		assertTrue(bean.getTestBeanMap().values().contains(tb1));
		assertTrue(bean.getTestBeanMap().values().contains(tb2));
	}

	@Test
	public void testFieldInjectionWithMap() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		RootBeanDefinition bd = new RootBeanDefinition(MapFieldInjectionBean.class);
		bd.setScope(RootBeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", bd);
		TestBean tb1 = new TestBean();
		TestBean tb2 = new TestBean();
		bf.registerSingleton("testBean1", tb1);
		bf.registerSingleton("testBean2", tb1);

		MapFieldInjectionBean bean = (MapFieldInjectionBean) bf.getBean("annotatedBean");
		assertEquals(2, bean.getTestBeanMap().size());
		assertTrue(bean.getTestBeanMap().keySet().contains("testBean1"));
		assertTrue(bean.getTestBeanMap().keySet().contains("testBean2"));
		assertTrue(bean.getTestBeanMap().values().contains(tb1));
		assertTrue(bean.getTestBeanMap().values().contains(tb2));

		bean = (MapFieldInjectionBean) bf.getBean("annotatedBean");
		assertEquals(2, bean.getTestBeanMap().size());
		assertTrue(bean.getTestBeanMap().keySet().contains("testBean1"));
		assertTrue(bean.getTestBeanMap().keySet().contains("testBean2"));
		assertTrue(bean.getTestBeanMap().values().contains(tb1));
		assertTrue(bean.getTestBeanMap().values().contains(tb2));
	}

	@Test
	public void testMethodInjectionWithMap() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		RootBeanDefinition bd = new RootBeanDefinition(MapMethodInjectionBean.class);
		bd.setScope(RootBeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", bd);
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);

		MapMethodInjectionBean bean = (MapMethodInjectionBean) bf.getBean("annotatedBean");
		assertEquals(1, bean.getTestBeanMap().size());
		assertTrue(bean.getTestBeanMap().keySet().contains("testBean"));
		assertTrue(bean.getTestBeanMap().values().contains(tb));
		assertSame(tb, bean.getTestBean());

		bean = (MapMethodInjectionBean) bf.getBean("annotatedBean");
		assertEquals(1, bean.getTestBeanMap().size());
		assertTrue(bean.getTestBeanMap().keySet().contains("testBean"));
		assertTrue(bean.getTestBeanMap().values().contains(tb));
		assertSame(tb, bean.getTestBean());
	}

	@Test
	public void testMethodInjectionWithMapAndMultipleMatches() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(MapMethodInjectionBean.class));
		bf.registerBeanDefinition("testBean1", new RootBeanDefinition(TestBean.class));
		bf.registerBeanDefinition("testBean2", new RootBeanDefinition(TestBean.class));

		try {
			bf.getBean("annotatedBean");
			fail("should have failed, more than one bean of type");
		}
		catch (BeanCreationException e) {
			// expected
		}
		bf.destroySingletons();
	}

	@Test
	public void testMethodInjectionWithMapAndMultipleMatchesButOnlyOneAutowireCandidate() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(MapMethodInjectionBean.class));
		bf.registerBeanDefinition("testBean1", new RootBeanDefinition(TestBean.class));
		RootBeanDefinition rbd2 = new RootBeanDefinition(TestBean.class);
		rbd2.setAutowireCandidate(false);
		bf.registerBeanDefinition("testBean2", rbd2);

		MapMethodInjectionBean bean = (MapMethodInjectionBean) bf.getBean("annotatedBean");
		TestBean tb = (TestBean) bf.getBean("testBean1");
		assertEquals(1, bean.getTestBeanMap().size());
		assertTrue(bean.getTestBeanMap().keySet().contains("testBean1"));
		assertTrue(bean.getTestBeanMap().values().contains(tb));
		assertSame(tb, bean.getTestBean());
		bf.destroySingletons();
	}

	@Test
	public void testMethodInjectionWithMapAndNoMatches() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(MapMethodInjectionBean.class));

		MapMethodInjectionBean bean = (MapMethodInjectionBean) bf.getBean("annotatedBean");
		assertNull(bean.getTestBeanMap());
		assertNull(bean.getTestBean());
		bf.destroySingletons();
	}

	@Test
	public void testObjectFactoryInjection() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(ObjectFactoryInjectionBean.class));
		bf.registerBeanDefinition("testBean", new RootBeanDefinition(TestBean.class));

		ObjectFactoryInjectionBean bean = (ObjectFactoryInjectionBean) bf.getBean("annotatedBean");
		assertSame(bf.getBean("testBean"), bean.getTestBean());
		bf.destroySingletons();
	}

	@Test
	public void testObjectFactoryInjectionIntoPrototypeBean() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		RootBeanDefinition annotatedBeanDefinition = new RootBeanDefinition(ObjectFactoryInjectionBean.class);
		annotatedBeanDefinition.setScope(BeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", annotatedBeanDefinition);
		bf.registerBeanDefinition("testBean", new RootBeanDefinition(TestBean.class));

		ObjectFactoryInjectionBean bean = (ObjectFactoryInjectionBean) bf.getBean("annotatedBean");
		assertSame(bf.getBean("testBean"), bean.getTestBean());
		ObjectFactoryInjectionBean anotherBean = (ObjectFactoryInjectionBean) bf.getBean("annotatedBean");
		assertNotSame(anotherBean, bean);
		assertSame(bf.getBean("testBean"), anotherBean.getTestBean());
	}

	@Test
	public void testObjectFactoryQualifierInjection() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		bf.setAutowireCandidateResolver(new QualifierAnnotationAutowireCandidateResolver());
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(ObjectFactoryQualifierInjectionBean.class));
		RootBeanDefinition bd = new RootBeanDefinition(TestBean.class);
		bd.addQualifier(new AutowireCandidateQualifier(Qualifier.class, "testBean"));
		bf.registerBeanDefinition("testBean", bd);
		bf.registerBeanDefinition("testBean2", new RootBeanDefinition(TestBean.class));

		ObjectFactoryQualifierInjectionBean bean = (ObjectFactoryQualifierInjectionBean) bf.getBean("annotatedBean");
		assertSame(bf.getBean("testBean"), bean.getTestBean());
		bf.destroySingletons();
	}

	@Test
	public void testObjectFactorySerialization() throws Exception {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		bf.registerBeanDefinition("annotatedBean", new RootBeanDefinition(ObjectFactoryInjectionBean.class));
		bf.registerBeanDefinition("testBean", new RootBeanDefinition(TestBean.class));
		bf.setSerializationId("test");

		ObjectFactoryInjectionBean bean = (ObjectFactoryInjectionBean) bf.getBean("annotatedBean");
		assertSame(bf.getBean("testBean"), bean.getTestBean());
		bean = (ObjectFactoryInjectionBean) SerializationTestUtils.serializeAndDeserialize(bean);
		assertSame(bf.getBean("testBean"), bean.getTestBean());
		bf.destroySingletons();
	}

	@Test
	public void testCustomAnnotationRequiredFieldResourceInjection() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setAutowiredAnnotationType(MyAutowired.class);
		bpp.setRequiredParameterName("optional");
		bpp.setRequiredParameterValue(false);
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		bf.registerBeanDefinition("customBean", new RootBeanDefinition(
				CustomAnnotationRequiredFieldResourceInjectionBean.class));
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);

		CustomAnnotationRequiredFieldResourceInjectionBean bean = (CustomAnnotationRequiredFieldResourceInjectionBean) bf.getBean("customBean");
		assertSame(tb, bean.getTestBean());
		bf.destroySingletons();
	}

	@Test
	public void testCustomAnnotationRequiredFieldResourceInjectionFailsWhenNoDependencyFound() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setAutowiredAnnotationType(MyAutowired.class);
		bpp.setRequiredParameterName("optional");
		bpp.setRequiredParameterValue(false);
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		bf.registerBeanDefinition("customBean", new RootBeanDefinition(
				CustomAnnotationRequiredFieldResourceInjectionBean.class));

		try {
			bf.getBean("customBean");
			fail("expected BeanCreationException; no dependency available for required field");
		}
		catch (BeanCreationException e) {
			// expected
		}
		bf.destroySingletons();
	}

	@Test
	public void testCustomAnnotationRequiredFieldResourceInjectionFailsWhenMultipleDependenciesFound() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setAutowiredAnnotationType(MyAutowired.class);
		bpp.setRequiredParameterName("optional");
		bpp.setRequiredParameterValue(false);
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		bf.registerBeanDefinition("customBean", new RootBeanDefinition(
				CustomAnnotationRequiredFieldResourceInjectionBean.class));
		TestBean tb1 = new TestBean();
		bf.registerSingleton("testBean1", tb1);
		TestBean tb2 = new TestBean();
		bf.registerSingleton("testBean2", tb2);

		try {
			bf.getBean("customBean");
			fail("expected BeanCreationException; multiple beans of dependency type available");
		}
		catch (BeanCreationException e) {
			// expected
		}
		bf.destroySingletons();
	}

	@Test
	public void testCustomAnnotationRequiredMethodResourceInjection() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setAutowiredAnnotationType(MyAutowired.class);
		bpp.setRequiredParameterName("optional");
		bpp.setRequiredParameterValue(false);
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		bf.registerBeanDefinition("customBean", new RootBeanDefinition(
				CustomAnnotationRequiredMethodResourceInjectionBean.class));
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);

		CustomAnnotationRequiredMethodResourceInjectionBean bean = (CustomAnnotationRequiredMethodResourceInjectionBean) bf.getBean("customBean");
		assertSame(tb, bean.getTestBean());
		bf.destroySingletons();
	}

	@Test
	public void testCustomAnnotationRequiredMethodResourceInjectionFailsWhenNoDependencyFound() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setAutowiredAnnotationType(MyAutowired.class);
		bpp.setRequiredParameterName("optional");
		bpp.setRequiredParameterValue(false);
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		bf.registerBeanDefinition("customBean", new RootBeanDefinition(
				CustomAnnotationRequiredMethodResourceInjectionBean.class));

		try {
			bf.getBean("customBean");
			fail("expected BeanCreationException; no dependency available for required method");
		}
		catch (BeanCreationException e) {
			// expected
		}
		bf.destroySingletons();
	}

	@Test
	public void testCustomAnnotationRequiredMethodResourceInjectionFailsWhenMultipleDependenciesFound() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setAutowiredAnnotationType(MyAutowired.class);
		bpp.setRequiredParameterName("optional");
		bpp.setRequiredParameterValue(false);
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		bf.registerBeanDefinition("customBean", new RootBeanDefinition(
				CustomAnnotationRequiredMethodResourceInjectionBean.class));
		TestBean tb1 = new TestBean();
		bf.registerSingleton("testBean1", tb1);
		TestBean tb2 = new TestBean();
		bf.registerSingleton("testBean2", tb2);

		try {
			bf.getBean("customBean");
			fail("expected BeanCreationException; multiple beans of dependency type available");
		}
		catch (BeanCreationException e) {
			// expected
		}
		bf.destroySingletons();
	}

	@Test
	public void testCustomAnnotationOptionalFieldResourceInjection() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setAutowiredAnnotationType(MyAutowired.class);
		bpp.setRequiredParameterName("optional");
		bpp.setRequiredParameterValue(false);
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		bf.registerBeanDefinition("customBean", new RootBeanDefinition(
				CustomAnnotationOptionalFieldResourceInjectionBean.class));
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);

		CustomAnnotationOptionalFieldResourceInjectionBean bean = (CustomAnnotationOptionalFieldResourceInjectionBean) bf.getBean("customBean");
		assertSame(tb, bean.getTestBean3());
		assertNull(bean.getTestBean());
		assertNull(bean.getTestBean2());
		bf.destroySingletons();
	}

	@Test
	public void testCustomAnnotationOptionalFieldResourceInjectionWhenNoDependencyFound() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setAutowiredAnnotationType(MyAutowired.class);
		bpp.setRequiredParameterName("optional");
		bpp.setRequiredParameterValue(false);
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		bf.registerBeanDefinition("customBean", new RootBeanDefinition(
				CustomAnnotationOptionalFieldResourceInjectionBean.class));

		CustomAnnotationOptionalFieldResourceInjectionBean bean = (CustomAnnotationOptionalFieldResourceInjectionBean) bf.getBean("customBean");
		assertNull(bean.getTestBean3());
		assertNull(bean.getTestBean());
		assertNull(bean.getTestBean2());
		bf.destroySingletons();
	}

	@Test
	public void testCustomAnnotationOptionalFieldResourceInjectionWhenMultipleDependenciesFound() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setAutowiredAnnotationType(MyAutowired.class);
		bpp.setRequiredParameterName("optional");
		bpp.setRequiredParameterValue(false);
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		bf.registerBeanDefinition("customBean", new RootBeanDefinition(
				CustomAnnotationOptionalFieldResourceInjectionBean.class));
		TestBean tb1 = new TestBean();
		bf.registerSingleton("testBean1", tb1);
		TestBean tb2 = new TestBean();
		bf.registerSingleton("testBean2", tb2);

		try {
			bf.getBean("customBean");
			fail("expected BeanCreationException; multiple beans of dependency type available");
		}
		catch (BeanCreationException e) {
			// expected
		}
		bf.destroySingletons();
	}

	@Test
	public void testCustomAnnotationOptionalMethodResourceInjection() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setAutowiredAnnotationType(MyAutowired.class);
		bpp.setRequiredParameterName("optional");
		bpp.setRequiredParameterValue(false);
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		bf.registerBeanDefinition("customBean", new RootBeanDefinition(
				CustomAnnotationOptionalMethodResourceInjectionBean.class));
		TestBean tb = new TestBean();
		bf.registerSingleton("testBean", tb);

		CustomAnnotationOptionalMethodResourceInjectionBean bean = (CustomAnnotationOptionalMethodResourceInjectionBean) bf.getBean("customBean");
		assertSame(tb, bean.getTestBean3());
		assertNull(bean.getTestBean());
		assertNull(bean.getTestBean2());
		bf.destroySingletons();
	}

	@Test
	public void testCustomAnnotationOptionalMethodResourceInjectionWhenNoDependencyFound() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setAutowiredAnnotationType(MyAutowired.class);
		bpp.setRequiredParameterName("optional");
		bpp.setRequiredParameterValue(false);
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		bf.registerBeanDefinition("customBean", new RootBeanDefinition(
				CustomAnnotationOptionalMethodResourceInjectionBean.class));

		CustomAnnotationOptionalMethodResourceInjectionBean bean = (CustomAnnotationOptionalMethodResourceInjectionBean) bf.getBean("customBean");
		assertNull(bean.getTestBean3());
		assertNull(bean.getTestBean());
		assertNull(bean.getTestBean2());
		bf.destroySingletons();
	}

	@Test
	public void testCustomAnnotationOptionalMethodResourceInjectionWhenMultipleDependenciesFound() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setAutowiredAnnotationType(MyAutowired.class);
		bpp.setRequiredParameterName("optional");
		bpp.setRequiredParameterValue(false);
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		bf.registerBeanDefinition("customBean", new RootBeanDefinition(
				CustomAnnotationOptionalMethodResourceInjectionBean.class));
		TestBean tb1 = new TestBean();
		bf.registerSingleton("testBean1", tb1);
		TestBean tb2 = new TestBean();
		bf.registerSingleton("testBean2", tb2);

		try {
			bf.getBean("customBean");
			fail("expected BeanCreationException; multiple beans of dependency type available");
		}
		catch (BeanCreationException e) {
			// expected
		}
		bf.destroySingletons();
	}

	/**
	 * Verifies that a dependency on a {@link FactoryBean} can be autowired via
	 * {@link Autowired @Autowired}, specifically addressing the JIRA issue
	 * raised in <a
	 * href="http://opensource.atlassian.com/projects/spring/browse/SPR-4040"
	 * target="_blank">SPR-4040</a>.
	 */
	@Test
	public void testBeanAutowiredWithFactoryBean() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		bf.registerBeanDefinition("factoryBeanDependentBean", new RootBeanDefinition(FactoryBeanDependentBean.class));
		bf.registerSingleton("stringFactoryBean", new StringFactoryBean());

		final StringFactoryBean factoryBean = (StringFactoryBean) bf.getBean("&stringFactoryBean");
		final FactoryBeanDependentBean bean = (FactoryBeanDependentBean) bf.getBean("factoryBeanDependentBean");

		assertNotNull("The singleton StringFactoryBean should have been registered.", factoryBean);
		assertNotNull("The factoryBeanDependentBean should have been registered.", bean);
		assertEquals("The FactoryBeanDependentBean should have been autowired 'by type' with the StringFactoryBean.",
				factoryBean, bean.getFactoryBean());

		bf.destroySingletons();
	}

	@Test
	public void testGenericsBasedFieldInjection() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		bf.setAutowireCandidateResolver(new QualifierAnnotationAutowireCandidateResolver());
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		RootBeanDefinition bd = new RootBeanDefinition(RepositoryFieldInjectionBean.class);
		bd.setScope(RootBeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", bd);
		StringRepository sr = new StringRepository();
		bf.registerSingleton("stringRepo", sr);
		IntegerRepository ir = new IntegerRepository();
		bf.registerSingleton("integerRepo", ir);

		RepositoryFieldInjectionBean bean = (RepositoryFieldInjectionBean) bf.getBean("annotatedBean");
		assertSame(sr, bean.stringRepository);
		assertSame(ir, bean.integerRepository);
		assertSame(1, bean.stringRepositoryArray.length);
		assertSame(1, bean.integerRepositoryArray.length);
		assertSame(sr, bean.stringRepositoryArray[0]);
		assertSame(ir, bean.integerRepositoryArray[0]);
		assertSame(1, bean.stringRepositoryList.size());
		assertSame(1, bean.integerRepositoryList.size());
		assertSame(sr, bean.stringRepositoryList.get(0));
		assertSame(ir, bean.integerRepositoryList.get(0));
		assertSame(1, bean.stringRepositoryMap.size());
		assertSame(1, bean.integerRepositoryMap.size());
		assertSame(sr, bean.stringRepositoryMap.get("stringRepo"));
		assertSame(ir, bean.integerRepositoryMap.get("integerRepo"));
	}

	@Test
	public void testGenericsBasedFieldInjectionWithSubstitutedVariables() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		bf.setAutowireCandidateResolver(new QualifierAnnotationAutowireCandidateResolver());
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		RootBeanDefinition bd = new RootBeanDefinition(RepositoryFieldInjectionBeanWithSubstitutedVariables.class);
		bd.setScope(RootBeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", bd);
		StringRepository sr = new StringRepository();
		bf.registerSingleton("stringRepo", sr);
		IntegerRepository ir = new IntegerRepository();
		bf.registerSingleton("integerRepo", ir);

		RepositoryFieldInjectionBeanWithSubstitutedVariables bean = (RepositoryFieldInjectionBeanWithSubstitutedVariables) bf.getBean("annotatedBean");
		assertSame(sr, bean.stringRepository);
		assertSame(ir, bean.integerRepository);
		assertSame(1, bean.stringRepositoryArray.length);
		assertSame(1, bean.integerRepositoryArray.length);
		assertSame(sr, bean.stringRepositoryArray[0]);
		assertSame(ir, bean.integerRepositoryArray[0]);
		assertSame(1, bean.stringRepositoryList.size());
		assertSame(1, bean.integerRepositoryList.size());
		assertSame(sr, bean.stringRepositoryList.get(0));
		assertSame(ir, bean.integerRepositoryList.get(0));
		assertSame(1, bean.stringRepositoryMap.size());
		assertSame(1, bean.integerRepositoryMap.size());
		assertSame(sr, bean.stringRepositoryMap.get("stringRepo"));
		assertSame(ir, bean.integerRepositoryMap.get("integerRepo"));
	}

	@Test
	public void testGenericsBasedFieldInjectionWithQualifiers() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		bf.setAutowireCandidateResolver(new QualifierAnnotationAutowireCandidateResolver());
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		RootBeanDefinition bd = new RootBeanDefinition(RepositoryFieldInjectionBeanWithQualifiers.class);
		bd.setScope(RootBeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", bd);
		StringRepository sr = new StringRepository();
		bf.registerSingleton("stringRepo", sr);
		IntegerRepository ir = new IntegerRepository();
		bf.registerSingleton("integerRepo", ir);

		RepositoryFieldInjectionBeanWithQualifiers bean = (RepositoryFieldInjectionBeanWithQualifiers) bf.getBean("annotatedBean");
		assertSame(sr, bean.stringRepository);
		assertSame(ir, bean.integerRepository);
		assertSame(1, bean.stringRepositoryArray.length);
		assertSame(1, bean.integerRepositoryArray.length);
		assertSame(sr, bean.stringRepositoryArray[0]);
		assertSame(ir, bean.integerRepositoryArray[0]);
		assertSame(1, bean.stringRepositoryList.size());
		assertSame(1, bean.integerRepositoryList.size());
		assertSame(sr, bean.stringRepositoryList.get(0));
		assertSame(ir, bean.integerRepositoryList.get(0));
		assertSame(1, bean.stringRepositoryMap.size());
		assertSame(1, bean.integerRepositoryMap.size());
		assertSame(sr, bean.stringRepositoryMap.get("stringRepo"));
		assertSame(ir, bean.integerRepositoryMap.get("integerRepo"));
	}

	@Test
	public void testGenericsBasedFieldInjectionWithMocks() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		bf.setAutowireCandidateResolver(new QualifierAnnotationAutowireCandidateResolver());
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		RootBeanDefinition bd = new RootBeanDefinition(RepositoryFieldInjectionBeanWithQualifiers.class);
		bd.setScope(RootBeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", bd);

		RootBeanDefinition rbd = new RootBeanDefinition(MocksControl.class);
		bf.registerBeanDefinition("mocksControl", rbd);
		rbd = new RootBeanDefinition();
		rbd.setFactoryBeanName("mocksControl");
		rbd.setFactoryMethodName("createMock");
		rbd.getConstructorArgumentValues().addGenericArgumentValue(Repository.class);
		bf.registerBeanDefinition("stringRepo", rbd);
		rbd = new RootBeanDefinition();
		rbd.setFactoryBeanName("mocksControl");
		rbd.setFactoryMethodName("createMock");
		rbd.getConstructorArgumentValues().addGenericArgumentValue(Repository.class);
		bf.registerBeanDefinition("integerRepo", rbd);

		RepositoryFieldInjectionBeanWithQualifiers bean = (RepositoryFieldInjectionBeanWithQualifiers) bf.getBean("annotatedBean");
		Repository sr = bf.getBean("stringRepo", Repository.class);
		Repository ir = bf.getBean("integerRepo", Repository.class);
		assertSame(sr, bean.stringRepository);
		assertSame(ir, bean.integerRepository);
		assertSame(1, bean.stringRepositoryArray.length);
		assertSame(1, bean.integerRepositoryArray.length);
		assertSame(sr, bean.stringRepositoryArray[0]);
		assertSame(ir, bean.integerRepositoryArray[0]);
		assertSame(1, bean.stringRepositoryList.size());
		assertSame(1, bean.integerRepositoryList.size());
		assertSame(sr, bean.stringRepositoryList.get(0));
		assertSame(ir, bean.integerRepositoryList.get(0));
		assertSame(1, bean.stringRepositoryMap.size());
		assertSame(1, bean.integerRepositoryMap.size());
		assertSame(sr, bean.stringRepositoryMap.get("stringRepo"));
		assertSame(ir, bean.integerRepositoryMap.get("integerRepo"));
	}

	@Test
	public void testGenericsBasedFieldInjectionWithSimpleMatch() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		bf.setAutowireCandidateResolver(new QualifierAnnotationAutowireCandidateResolver());
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		RootBeanDefinition bd = new RootBeanDefinition(RepositoryFieldInjectionBeanWithSimpleMatch.class);
		bd.setScope(RootBeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", bd);

		bf.registerSingleton("repo", new StringRepository());

		RepositoryFieldInjectionBeanWithSimpleMatch bean = (RepositoryFieldInjectionBeanWithSimpleMatch) bf.getBean("annotatedBean");
		Repository repo = bf.getBean("repo", Repository.class);
		assertSame(repo, bean.repository);
		assertSame(repo, bean.stringRepository);
		assertSame(1, bean.repositoryArray.length);
		assertSame(1, bean.stringRepositoryArray.length);
		assertSame(repo, bean.repositoryArray[0]);
		assertSame(repo, bean.stringRepositoryArray[0]);
		assertSame(1, bean.repositoryList.size());
		assertSame(1, bean.stringRepositoryList.size());
		assertSame(repo, bean.repositoryList.get(0));
		assertSame(repo, bean.stringRepositoryList.get(0));
		assertSame(1, bean.repositoryMap.size());
		assertSame(1, bean.stringRepositoryMap.size());
		assertSame(repo, bean.repositoryMap.get("repo"));
		assertSame(repo, bean.stringRepositoryMap.get("repo"));
	}

	@Test
	public void testGenericsBasedFieldInjectionWithSimpleMatchAndMock() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		bf.setAutowireCandidateResolver(new QualifierAnnotationAutowireCandidateResolver());
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		RootBeanDefinition bd = new RootBeanDefinition(RepositoryFieldInjectionBeanWithSimpleMatch.class);
		bd.setScope(RootBeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", bd);

		RootBeanDefinition rbd = new RootBeanDefinition(MocksControl.class);
		bf.registerBeanDefinition("mocksControl", rbd);
		rbd = new RootBeanDefinition();
		rbd.setFactoryBeanName("mocksControl");
		rbd.setFactoryMethodName("createMock");
		rbd.getConstructorArgumentValues().addGenericArgumentValue(Repository.class);
		bf.registerBeanDefinition("repo", rbd);

		RepositoryFieldInjectionBeanWithSimpleMatch bean = (RepositoryFieldInjectionBeanWithSimpleMatch) bf.getBean("annotatedBean");
		Repository repo = bf.getBean("repo", Repository.class);
		assertSame(repo, bean.repository);
		assertSame(repo, bean.stringRepository);
		assertSame(1, bean.repositoryArray.length);
		assertSame(1, bean.stringRepositoryArray.length);
		assertSame(repo, bean.repositoryArray[0]);
		assertSame(repo, bean.stringRepositoryArray[0]);
		assertSame(1, bean.repositoryList.size());
		assertSame(1, bean.stringRepositoryList.size());
		assertSame(repo, bean.repositoryList.get(0));
		assertSame(repo, bean.stringRepositoryList.get(0));
		assertSame(1, bean.repositoryMap.size());
		assertSame(1, bean.stringRepositoryMap.size());
		assertSame(repo, bean.repositoryMap.get("repo"));
		assertSame(repo, bean.stringRepositoryMap.get("repo"));
	}

	@Test
	public void testGenericsBasedMethodInjection() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		bf.setAutowireCandidateResolver(new QualifierAnnotationAutowireCandidateResolver());
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		RootBeanDefinition bd = new RootBeanDefinition(RepositoryMethodInjectionBean.class);
		bd.setScope(RootBeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", bd);
		StringRepository sr = new StringRepository();
		bf.registerSingleton("stringRepo", sr);
		IntegerRepository ir = new IntegerRepository();
		bf.registerSingleton("integerRepo", ir);

		RepositoryMethodInjectionBean bean = (RepositoryMethodInjectionBean) bf.getBean("annotatedBean");
		assertSame(sr, bean.stringRepository);
		assertSame(ir, bean.integerRepository);
		assertSame(1, bean.stringRepositoryArray.length);
		assertSame(1, bean.integerRepositoryArray.length);
		assertSame(sr, bean.stringRepositoryArray[0]);
		assertSame(ir, bean.integerRepositoryArray[0]);
		assertSame(1, bean.stringRepositoryList.size());
		assertSame(1, bean.integerRepositoryList.size());
		assertSame(sr, bean.stringRepositoryList.get(0));
		assertSame(ir, bean.integerRepositoryList.get(0));
		assertSame(1, bean.stringRepositoryMap.size());
		assertSame(1, bean.integerRepositoryMap.size());
		assertSame(sr, bean.stringRepositoryMap.get("stringRepo"));
		assertSame(ir, bean.integerRepositoryMap.get("integerRepo"));
	}

	@Test
	public void testGenericsBasedMethodInjectionWithSubstitutedVariables() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		bf.setAutowireCandidateResolver(new QualifierAnnotationAutowireCandidateResolver());
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		RootBeanDefinition bd = new RootBeanDefinition(RepositoryMethodInjectionBeanWithSubstitutedVariables.class);
		bd.setScope(RootBeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", bd);
		StringRepository sr = new StringRepository();
		bf.registerSingleton("stringRepo", sr);
		IntegerRepository ir = new IntegerRepository();
		bf.registerSingleton("integerRepo", ir);

		RepositoryMethodInjectionBeanWithSubstitutedVariables bean = (RepositoryMethodInjectionBeanWithSubstitutedVariables) bf.getBean("annotatedBean");
		assertSame(sr, bean.stringRepository);
		assertSame(ir, bean.integerRepository);
		assertSame(1, bean.stringRepositoryArray.length);
		assertSame(1, bean.integerRepositoryArray.length);
		assertSame(sr, bean.stringRepositoryArray[0]);
		assertSame(ir, bean.integerRepositoryArray[0]);
		assertSame(1, bean.stringRepositoryList.size());
		assertSame(1, bean.integerRepositoryList.size());
		assertSame(sr, bean.stringRepositoryList.get(0));
		assertSame(ir, bean.integerRepositoryList.get(0));
		assertSame(1, bean.stringRepositoryMap.size());
		assertSame(1, bean.integerRepositoryMap.size());
		assertSame(sr, bean.stringRepositoryMap.get("stringRepo"));
		assertSame(ir, bean.integerRepositoryMap.get("integerRepo"));
	}

	@Test
	public void testGenericsBasedConstructorInjection() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		bf.setAutowireCandidateResolver(new QualifierAnnotationAutowireCandidateResolver());
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		RootBeanDefinition bd = new RootBeanDefinition(RepositoryConstructorInjectionBean.class);
		bd.setScope(RootBeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", bd);
		StringRepository sr = new StringRepository();
		bf.registerSingleton("stringRepo", sr);
		IntegerRepository ir = new IntegerRepository();
		bf.registerSingleton("integerRepo", ir);

		RepositoryConstructorInjectionBean bean = (RepositoryConstructorInjectionBean) bf.getBean("annotatedBean");
		assertSame(sr, bean.stringRepository);
		assertSame(ir, bean.integerRepository);
		assertSame(1, bean.stringRepositoryArray.length);
		assertSame(1, bean.integerRepositoryArray.length);
		assertSame(sr, bean.stringRepositoryArray[0]);
		assertSame(ir, bean.integerRepositoryArray[0]);
		assertSame(1, bean.stringRepositoryList.size());
		assertSame(1, bean.integerRepositoryList.size());
		assertSame(sr, bean.stringRepositoryList.get(0));
		assertSame(ir, bean.integerRepositoryList.get(0));
		assertSame(1, bean.stringRepositoryMap.size());
		assertSame(1, bean.integerRepositoryMap.size());
		assertSame(sr, bean.stringRepositoryMap.get("stringRepo"));
		assertSame(ir, bean.integerRepositoryMap.get("integerRepo"));
	}

	@Test
	public void testGenericsBasedConstructorInjectionWithNonTypedTarget() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		bf.setAutowireCandidateResolver(new QualifierAnnotationAutowireCandidateResolver());
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		RootBeanDefinition bd = new RootBeanDefinition(RepositoryConstructorInjectionBean.class);
		bd.setScope(RootBeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", bd);
		GenericRepository gr = new GenericRepository();
		bf.registerSingleton("genericRepo", gr);

		RepositoryConstructorInjectionBean bean = (RepositoryConstructorInjectionBean) bf.getBean("annotatedBean");
		assertSame(gr, bean.stringRepository);
		assertSame(gr, bean.integerRepository);
		assertSame(1, bean.stringRepositoryArray.length);
		assertSame(1, bean.integerRepositoryArray.length);
		assertSame(gr, bean.stringRepositoryArray[0]);
		assertSame(gr, bean.integerRepositoryArray[0]);
		assertSame(1, bean.stringRepositoryList.size());
		assertSame(1, bean.integerRepositoryList.size());
		assertSame(gr, bean.stringRepositoryList.get(0));
		assertSame(gr, bean.integerRepositoryList.get(0));
		assertSame(1, bean.stringRepositoryMap.size());
		assertSame(1, bean.integerRepositoryMap.size());
		assertSame(gr, bean.stringRepositoryMap.get("genericRepo"));
		assertSame(gr, bean.integerRepositoryMap.get("genericRepo"));
	}

	@Test
	public void testGenericsBasedConstructorInjectionWithNonGenericTarget() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		bf.setAutowireCandidateResolver(new QualifierAnnotationAutowireCandidateResolver());
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		RootBeanDefinition bd = new RootBeanDefinition(RepositoryConstructorInjectionBean.class);
		bd.setScope(RootBeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", bd);
		SimpleRepository ngr = new SimpleRepository();
		bf.registerSingleton("simpleRepo", ngr);

		RepositoryConstructorInjectionBean bean = (RepositoryConstructorInjectionBean) bf.getBean("annotatedBean");
		assertSame(ngr, bean.stringRepository);
		assertSame(ngr, bean.integerRepository);
		assertSame(1, bean.stringRepositoryArray.length);
		assertSame(1, bean.integerRepositoryArray.length);
		assertSame(ngr, bean.stringRepositoryArray[0]);
		assertSame(ngr, bean.integerRepositoryArray[0]);
		assertSame(1, bean.stringRepositoryList.size());
		assertSame(1, bean.integerRepositoryList.size());
		assertSame(ngr, bean.stringRepositoryList.get(0));
		assertSame(ngr, bean.integerRepositoryList.get(0));
		assertSame(1, bean.stringRepositoryMap.size());
		assertSame(1, bean.integerRepositoryMap.size());
		assertSame(ngr, bean.stringRepositoryMap.get("simpleRepo"));
		assertSame(ngr, bean.integerRepositoryMap.get("simpleRepo"));
	}

	@Test
	public void testGenericsBasedConstructorInjectionWithMixedTargets() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		bf.setAutowireCandidateResolver(new QualifierAnnotationAutowireCandidateResolver());
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		RootBeanDefinition bd = new RootBeanDefinition(RepositoryConstructorInjectionBean.class);
		bd.setScope(RootBeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", bd);
		StringRepository sr = new StringRepository();
		bf.registerSingleton("stringRepo", sr);
		GenericRepository gr = new GenericRepositorySubclass();
		bf.registerSingleton("genericRepo", gr);

		RepositoryConstructorInjectionBean bean = (RepositoryConstructorInjectionBean) bf.getBean("annotatedBean");
		assertSame(sr, bean.stringRepository);
		assertSame(gr, bean.integerRepository);
		assertSame(1, bean.stringRepositoryArray.length);
		assertSame(1, bean.integerRepositoryArray.length);
		assertSame(sr, bean.stringRepositoryArray[0]);
		assertSame(gr, bean.integerRepositoryArray[0]);
		assertSame(1, bean.stringRepositoryList.size());
		assertSame(1, bean.integerRepositoryList.size());
		assertSame(sr, bean.stringRepositoryList.get(0));
		assertSame(gr, bean.integerRepositoryList.get(0));
		assertSame(1, bean.stringRepositoryMap.size());
		assertSame(1, bean.integerRepositoryMap.size());
		assertSame(sr, bean.stringRepositoryMap.get("stringRepo"));
		assertSame(gr, bean.integerRepositoryMap.get("genericRepo"));
	}

	@Test
	public void testGenericsBasedConstructorInjectionWithMixedTargetsIncludingNonGeneric() {
		DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
		bf.setAutowireCandidateResolver(new QualifierAnnotationAutowireCandidateResolver());
		AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
		bpp.setBeanFactory(bf);
		bf.addBeanPostProcessor(bpp);
		RootBeanDefinition bd = new RootBeanDefinition(RepositoryConstructorInjectionBean.class);
		bd.setScope(RootBeanDefinition.SCOPE_PROTOTYPE);
		bf.registerBeanDefinition("annotatedBean", bd);
		StringRepository sr = new StringRepository();
		bf.registerSingleton("stringRepo", sr);
		SimpleRepository ngr = new SimpleRepositorySubclass();
		bf.registerSingleton("simpleRepo", ngr);

		RepositoryConstructorInjectionBean bean = (RepositoryConstructorInjectionBean) bf.getBean("annotatedBean");
		assertSame(sr, bean.stringRepository);
		assertSame(ngr, bean.integerRepository);
		assertSame(1, bean.stringRepositoryArray.length);
		assertSame(1, bean.integerRepositoryArray.length);
		assertSame(sr, bean.stringRepositoryArray[0]);
		assertSame(ngr, bean.integerRepositoryArray[0]);
		assertSame(1, bean.stringRepositoryList.size());
		assertSame(1, bean.integerRepositoryList.size());
		assertSame(sr, bean.stringRepositoryList.get(0));
		assertSame(ngr, bean.integerRepositoryList.get(0));
		assertSame(1, bean.stringRepositoryMap.size());
		assertSame(1, bean.integerRepositoryMap.size());
		assertSame(sr, bean.stringRepositoryMap.get("stringRepo"));
		assertSame(ngr, bean.integerRepositoryMap.get("simpleRepo"));
	}


	public static class ResourceInjectionBean {

		@Autowired(required = false)
		private TestBean testBean;

		private TestBean testBean2;

		@Autowired
		public void setTestBean2(TestBean testBean2) {
			if (this.testBean2 != null) {
				throw new IllegalStateException("Already called");
			}
			this.testBean2 = testBean2;
		}

		public TestBean getTestBean() {
			return this.testBean;
		}

		public TestBean getTestBean2() {
			return this.testBean2;
		}
	}


	public static class ExtendedResourceInjectionBean<T> extends ResourceInjectionBean {

		@Autowired
		public final ITestBean testBean3 = null;

		private T nestedTestBean;

		private ITestBean testBean4;

		protected BeanFactory beanFactory;

		public boolean baseInjected = false;

		public ExtendedResourceInjectionBean() {
		}

		@Override
		@Autowired @Required
		public void setTestBean2(TestBean testBean2) {
			super.setTestBean2(testBean2);
		}

		@Autowired
		private void inject(ITestBean testBean4, T nestedTestBean) {
			this.testBean4 = testBean4;
			this.nestedTestBean = nestedTestBean;
		}

		@Autowired
		private void inject(ITestBean testBean4) {
			this.baseInjected = true;
		}

		@Autowired
		protected void initBeanFactory(BeanFactory beanFactory) {
			this.beanFactory = beanFactory;
		}

		public ITestBean getTestBean3() {
			return this.testBean3;
		}

		public ITestBean getTestBean4() {
			return this.testBean4;
		}

		public T getNestedTestBean() {
			return this.nestedTestBean;
		}

		public BeanFactory getBeanFactory() {
			return this.beanFactory;
		}
	}


	public static class TypedExtendedResourceInjectionBean extends ExtendedResourceInjectionBean<NestedTestBean> {

	}


	public static class OverriddenExtendedResourceInjectionBean extends ExtendedResourceInjectionBean<NestedTestBean> {

		public boolean subInjected = false;

		@Override
		public void setTestBean2(TestBean testBean2) {
			super.setTestBean2(testBean2);
		}

		@Override
		protected void initBeanFactory(BeanFactory beanFactory) {
			this.beanFactory = beanFactory;
		}

		@Autowired
		private void inject(ITestBean testBean4) {
			this.subInjected = true;
		}
	}


	public static class OptionalResourceInjectionBean extends ResourceInjectionBean {

		@Autowired(required = false)
		protected ITestBean testBean3;

		private IndexedTestBean indexedTestBean;

		private NestedTestBean[] nestedTestBeans;

		@Autowired(required = false)
		public NestedTestBean[] nestedTestBeansField;

		private ITestBean testBean4;

		@Override
		@Autowired(required = false)
		public void setTestBean2(TestBean testBean2) {
			super.setTestBean2(testBean2);
		}

		@Autowired(required = false)
		private void inject(ITestBean testBean4, NestedTestBean[] nestedTestBeans, IndexedTestBean indexedTestBean) {
			this.testBean4 = testBean4;
			this.indexedTestBean = indexedTestBean;
			this.nestedTestBeans = nestedTestBeans;
		}

		public ITestBean getTestBean3() {
			return this.testBean3;
		}

		public ITestBean getTestBean4() {
			return this.testBean4;
		}

		public IndexedTestBean getIndexedTestBean() {
			return this.indexedTestBean;
		}

		public NestedTestBean[] getNestedTestBeans() {
			return this.nestedTestBeans;
		}
	}


	public static class OptionalCollectionResourceInjectionBean extends ResourceInjectionBean {

		@Autowired(required = false)
		protected ITestBean testBean3;

		private IndexedTestBean indexedTestBean;

		private List<NestedTestBean> nestedTestBeans;

		public List<NestedTestBean> nestedTestBeansSetter;

		@Autowired(required = false)
		public List<NestedTestBean> nestedTestBeansField;

		private ITestBean testBean4;

		@Override
		@Autowired(required = false)
		public void setTestBean2(TestBean testBean2) {
			super.setTestBean2(testBean2);
		}

		@Autowired(required = false)
		private void inject(ITestBean testBean4, List<NestedTestBean> nestedTestBeans, IndexedTestBean indexedTestBean) {
			this.testBean4 = testBean4;
			this.indexedTestBean = indexedTestBean;
			this.nestedTestBeans = nestedTestBeans;
		}

		@Autowired(required = false)
		public void setNestedTestBeans(List<NestedTestBean> nestedTestBeans) {
			this.nestedTestBeansSetter = nestedTestBeans;
		}

		public ITestBean getTestBean3() {
			return this.testBean3;
		}

		public ITestBean getTestBean4() {
			return this.testBean4;
		}

		public IndexedTestBean getIndexedTestBean() {
			return this.indexedTestBean;
		}

		public List<NestedTestBean> getNestedTestBeans() {
			return this.nestedTestBeans;
		}
	}


	public static class ConstructorResourceInjectionBean extends ResourceInjectionBean {

		@Autowired
		protected ITestBean testBean3;

		private ITestBean testBean4;

		private NestedTestBean nestedTestBean;

		private ConfigurableListableBeanFactory beanFactory;


		public ConstructorResourceInjectionBean() {
			throw new UnsupportedOperationException();
		}

		public ConstructorResourceInjectionBean(ITestBean testBean3) {
			throw new UnsupportedOperationException();
		}

		@Autowired
		public ConstructorResourceInjectionBean(ITestBean testBean4, NestedTestBean nestedTestBean,
				ConfigurableListableBeanFactory beanFactory) {
			this.testBean4 = testBean4;
			this.nestedTestBean = nestedTestBean;
			this.beanFactory = beanFactory;
		}

		public ConstructorResourceInjectionBean(NestedTestBean nestedTestBean) {
			throw new UnsupportedOperationException();
		}

		public ConstructorResourceInjectionBean(ITestBean testBean3, ITestBean testBean4, NestedTestBean nestedTestBean) {
			throw new UnsupportedOperationException();
		}

		@Override
		@Autowired
		public void setTestBean2(TestBean testBean2) {
			super.setTestBean2(testBean2);
		}

		public ITestBean getTestBean3() {
			return this.testBean3;
		}

		public ITestBean getTestBean4() {
			return this.testBean4;
		}

		public NestedTestBean getNestedTestBean() {
			return this.nestedTestBean;
		}

		public ConfigurableListableBeanFactory getBeanFactory() {
			return this.beanFactory;
		}
	}


	public static class ConstructorsResourceInjectionBean {

		protected ITestBean testBean3;

		private ITestBean testBean4;

		private NestedTestBean[] nestedTestBeans;

		public ConstructorsResourceInjectionBean() {
		}

		@Autowired(required = false)
		public ConstructorsResourceInjectionBean(ITestBean testBean3) {
			this.testBean3 = testBean3;
		}

		@Autowired(required = false)
		public ConstructorsResourceInjectionBean(ITestBean testBean4, NestedTestBean[] nestedTestBeans) {
			this.testBean4 = testBean4;
			this.nestedTestBeans = nestedTestBeans;
		}

		public ConstructorsResourceInjectionBean(NestedTestBean nestedTestBean) {
			throw new UnsupportedOperationException();
		}

		public ConstructorsResourceInjectionBean(ITestBean testBean3, ITestBean testBean4, NestedTestBean nestedTestBean) {
			throw new UnsupportedOperationException();
		}

		public ITestBean getTestBean3() {
			return this.testBean3;
		}

		public ITestBean getTestBean4() {
			return this.testBean4;
		}

		public NestedTestBean[] getNestedTestBeans() {
			return this.nestedTestBeans;
		}
	}


	public static class ConstructorsCollectionResourceInjectionBean {

		protected ITestBean testBean3;

		private ITestBean testBean4;

		private List<NestedTestBean> nestedTestBeans;

		public ConstructorsCollectionResourceInjectionBean() {
		}

		@Autowired(required = false)
		public ConstructorsCollectionResourceInjectionBean(ITestBean testBean3) {
			this.testBean3 = testBean3;
		}

		@Autowired(required = false)
		public ConstructorsCollectionResourceInjectionBean(ITestBean testBean4, List<NestedTestBean> nestedTestBeans) {
			this.testBean4 = testBean4;
			this.nestedTestBeans = nestedTestBeans;
		}

		public ConstructorsCollectionResourceInjectionBean(NestedTestBean nestedTestBean) {
			throw new UnsupportedOperationException();
		}

		public ConstructorsCollectionResourceInjectionBean(ITestBean testBean3, ITestBean testBean4,
				NestedTestBean nestedTestBean) {
			throw new UnsupportedOperationException();
		}

		public ITestBean getTestBean3() {
			return this.testBean3;
		}

		public ITestBean getTestBean4() {
			return this.testBean4;
		}

		public List<NestedTestBean> getNestedTestBeans() {
			return this.nestedTestBeans;
		}
	}


	public static class MapConstructorInjectionBean {

		private Map<String, TestBean> testBeanMap;

		@Autowired
		public MapConstructorInjectionBean(Map<String, TestBean> testBeanMap) {
			this.testBeanMap = testBeanMap;
		}

		public Map<String, TestBean> getTestBeanMap() {
			return this.testBeanMap;
		}
	}


	public static class MapFieldInjectionBean {

		@Autowired
		private Map<String, TestBean> testBeanMap;


		public Map<String, TestBean> getTestBeanMap() {
			return this.testBeanMap;
		}
	}


	public static class MapMethodInjectionBean {

		private TestBean testBean;

		private Map<String, TestBean> testBeanMap;

		@Autowired(required = false)
		public void setTestBeanMap(TestBean testBean, Map<String, TestBean> testBeanMap) {
			this.testBean = testBean;
			this.testBeanMap = testBeanMap;
		}

		public TestBean getTestBean() {
			return this.testBean;
		}

		public Map<String, TestBean> getTestBeanMap() {
			return this.testBeanMap;
		}
	}


	@SuppressWarnings("serial")
	public static class ObjectFactoryInjectionBean implements Serializable {

		@Autowired
		private ObjectFactory<TestBean> testBeanFactory;

		public TestBean getTestBean() {
			return this.testBeanFactory.getObject();
		}
	}


	public static class ObjectFactoryQualifierInjectionBean {

		@Autowired
		@Qualifier("testBean")
		private ObjectFactory<?> testBeanFactory;

		public TestBean getTestBean() {
			return (TestBean) this.testBeanFactory.getObject();
		}
	}


	public static class CustomAnnotationRequiredFieldResourceInjectionBean {

		@MyAutowired(optional = false)
		private TestBean testBean;

		public TestBean getTestBean() {
			return this.testBean;
		}
	}


	public static class CustomAnnotationRequiredMethodResourceInjectionBean {

		private TestBean testBean;

		@MyAutowired(optional = false)
		public void setTestBean(TestBean testBean) {
			this.testBean = testBean;
		}

		public TestBean getTestBean() {
			return this.testBean;
		}
	}


	public static class CustomAnnotationOptionalFieldResourceInjectionBean extends ResourceInjectionBean {

		@MyAutowired(optional = true)
		private TestBean testBean3;

		public TestBean getTestBean3() {
			return this.testBean3;
		}
	}


	public static class CustomAnnotationOptionalMethodResourceInjectionBean extends ResourceInjectionBean {

		private TestBean testBean3;


		@MyAutowired(optional = true)
		protected void setTestBean3(TestBean testBean3) {
			this.testBean3 = testBean3;
		}

		public TestBean getTestBean3() {
			return this.testBean3;
		}
	}


	@Target({ElementType.METHOD, ElementType.FIELD})
	@Retention(RetentionPolicy.RUNTIME)
	public static @interface MyAutowired {

		boolean optional() default false;
	}


	/**
	 * Bean with a dependency on a {@link FactoryBean}.
	 */
	private static class FactoryBeanDependentBean {

		@Autowired
		private FactoryBean<?> factoryBean;

		public final FactoryBean<?> getFactoryBean() {
			return this.factoryBean;
		}
	}


	public static class StringFactoryBean implements FactoryBean<String> {

		@Override
		public String getObject() throws Exception {
			return "";
		}

		@Override
		public Class<String> getObjectType() {
			return String.class;
		}

		@Override
		public boolean isSingleton() {
			return true;
		}
	}


	public static class OrderedNestedTestBean extends NestedTestBean implements Ordered {

		private int order;

		public void setOrder(int order) {
			this.order = order;
		}

		@Override
		public int getOrder() {
			return this.order;
		}
	}


	@Order(1)
	public static class FixedOrder1NestedTestBean extends NestedTestBean {
	}

	@Order(2)
	public static class FixedOrder2NestedTestBean extends NestedTestBean {
	}


	public interface Repository<T> {
	}

	public static class StringRepository implements Repository<String> {
	}

	public static class IntegerRepository implements Repository<Integer> {
	}

	public static class GenericRepository<T> implements Repository<T> {
	}

	public static class GenericRepositorySubclass extends GenericRepository {
	}

	public static class SimpleRepository implements Repository {
	}

	public static class SimpleRepositorySubclass extends SimpleRepository {
	}


	public static class RepositoryFieldInjectionBean {

		@Autowired
		public Repository<String> stringRepository;

		@Autowired
		public Repository<Integer> integerRepository;

		@Autowired
		public Repository<String>[] stringRepositoryArray;

		@Autowired
		public Repository<Integer>[] integerRepositoryArray;

		@Autowired
		public List<Repository<String>> stringRepositoryList;

		@Autowired
		public List<Repository<Integer>> integerRepositoryList;

		@Autowired
		public Map<String, Repository<String>> stringRepositoryMap;

		@Autowired
		public Map<String, Repository<Integer>> integerRepositoryMap;
	}


	public static class RepositoryFieldInjectionBeanWithVariables<S, I> {

		@Autowired
		public Repository<S> stringRepository;

		@Autowired
		public Repository<I> integerRepository;

		@Autowired
		public Repository<S>[] stringRepositoryArray;

		@Autowired
		public Repository<I>[] integerRepositoryArray;

		@Autowired
		public List<Repository<S>> stringRepositoryList;

		@Autowired
		public List<Repository<I>> integerRepositoryList;

		@Autowired
		public Map<String, Repository<S>> stringRepositoryMap;

		@Autowired
		public Map<String, Repository<I>> integerRepositoryMap;
	}


	public static class RepositoryFieldInjectionBeanWithSubstitutedVariables
			extends RepositoryFieldInjectionBeanWithVariables<String, Integer> {
	}


	public static class RepositoryFieldInjectionBeanWithQualifiers {

		@Autowired @Qualifier("stringRepo")
		public Repository<?> stringRepository;

		@Autowired @Qualifier("integerRepo")
		public Repository integerRepository;

		@Autowired @Qualifier("stringRepo")
		public Repository<?>[] stringRepositoryArray;

		@Autowired @Qualifier("integerRepo")
		public Repository[] integerRepositoryArray;

		@Autowired @Qualifier("stringRepo")
		public List<Repository> stringRepositoryList;

		@Autowired @Qualifier("integerRepo")
		public List<Repository<?>> integerRepositoryList;

		@Autowired @Qualifier("stringRepo")
		public Map<String, Repository<?>> stringRepositoryMap;

		@Autowired @Qualifier("integerRepo")
		public Map<String, Repository> integerRepositoryMap;
	}


	public static class RepositoryFieldInjectionBeanWithSimpleMatch {

		@Autowired
		public Repository<?> repository;

		@Autowired
		public Repository<String> stringRepository;

		@Autowired
		public Repository[] repositoryArray;

		@Autowired
		public Repository<String>[] stringRepositoryArray;

		@Autowired
		public List<Repository> repositoryList;

		@Autowired
		public List<Repository<String>> stringRepositoryList;

		@Autowired
		public Map<String, Repository<?>> repositoryMap;

		@Autowired
		public Map<String, Repository<String>> stringRepositoryMap;
	}


	public static class RepositoryMethodInjectionBean {

		public Repository<String> stringRepository;

		public Repository<Integer> integerRepository;

		public Repository<String>[] stringRepositoryArray;

		public Repository<Integer>[] integerRepositoryArray;

		public List<Repository<String>> stringRepositoryList;

		public List<Repository<Integer>> integerRepositoryList;

		public Map<String, Repository<String>> stringRepositoryMap;

		public Map<String, Repository<Integer>> integerRepositoryMap;

		@Autowired
		public void setStringRepository(Repository<String> stringRepository) {
			this.stringRepository = stringRepository;
		}

		@Autowired
		public void setIntegerRepository(Repository<Integer> integerRepository) {
			this.integerRepository = integerRepository;
		}

		@Autowired
		public void setStringRepositoryArray(Repository<String>[] stringRepositoryArray) {
			this.stringRepositoryArray = stringRepositoryArray;
		}

		@Autowired
		public void setIntegerRepositoryArray(Repository<Integer>[] integerRepositoryArray) {
			this.integerRepositoryArray = integerRepositoryArray;
		}

		@Autowired
		public void setStringRepositoryList(List<Repository<String>> stringRepositoryList) {
			this.stringRepositoryList = stringRepositoryList;
		}

		@Autowired
		public void setIntegerRepositoryList(List<Repository<Integer>> integerRepositoryList) {
			this.integerRepositoryList = integerRepositoryList;
		}

		@Autowired
		public void setStringRepositoryMap(Map<String, Repository<String>> stringRepositoryMap) {
			this.stringRepositoryMap = stringRepositoryMap;
		}

		@Autowired
		public void setIntegerRepositoryMap(Map<String, Repository<Integer>> integerRepositoryMap) {
			this.integerRepositoryMap = integerRepositoryMap;
		}
	}


	public static class RepositoryMethodInjectionBeanWithVariables<S, I> {

		public Repository<S> stringRepository;

		public Repository<I> integerRepository;

		public Repository<S>[] stringRepositoryArray;

		public Repository<I>[] integerRepositoryArray;

		public List<Repository<S>> stringRepositoryList;

		public List<Repository<I>> integerRepositoryList;

		public Map<String, Repository<S>> stringRepositoryMap;

		public Map<String, Repository<I>> integerRepositoryMap;

		@Autowired
		public void setStringRepository(Repository<S> stringRepository) {
			this.stringRepository = stringRepository;
		}

		@Autowired
		public void setIntegerRepository(Repository<I> integerRepository) {
			this.integerRepository = integerRepository;
		}

		@Autowired
		public void setStringRepositoryArray(Repository<S>[] stringRepositoryArray) {
			this.stringRepositoryArray = stringRepositoryArray;
		}

		@Autowired
		public void setIntegerRepositoryArray(Repository<I>[] integerRepositoryArray) {
			this.integerRepositoryArray = integerRepositoryArray;
		}

		@Autowired
		public void setStringRepositoryList(List<Repository<S>> stringRepositoryList) {
			this.stringRepositoryList = stringRepositoryList;
		}

		@Autowired
		public void setIntegerRepositoryList(List<Repository<I>> integerRepositoryList) {
			this.integerRepositoryList = integerRepositoryList;
		}

		@Autowired
		public void setStringRepositoryMap(Map<String, Repository<S>> stringRepositoryMap) {
			this.stringRepositoryMap = stringRepositoryMap;
		}

		@Autowired
		public void setIntegerRepositoryMap(Map<String, Repository<I>> integerRepositoryMap) {
			this.integerRepositoryMap = integerRepositoryMap;
		}
	}


	public static class RepositoryMethodInjectionBeanWithSubstitutedVariables
			extends RepositoryMethodInjectionBeanWithVariables<String, Integer> {
	}


	public static class RepositoryConstructorInjectionBean {

		public Repository<String> stringRepository;

		public Repository<Integer> integerRepository;

		public Repository<String>[] stringRepositoryArray;

		public Repository<Integer>[] integerRepositoryArray;

		public List<Repository<String>> stringRepositoryList;

		public List<Repository<Integer>> integerRepositoryList;

		public Map<String, Repository<String>> stringRepositoryMap;

		public Map<String, Repository<Integer>> integerRepositoryMap;

		@Autowired
		public RepositoryConstructorInjectionBean(Repository<String> stringRepository, Repository<Integer> integerRepository,
				Repository<String>[] stringRepositoryArray, Repository<Integer>[] integerRepositoryArray,
				List<Repository<String>> stringRepositoryList, List<Repository<Integer>> integerRepositoryList,
				Map<String, Repository<String>> stringRepositoryMap, Map<String, Repository<Integer>> integerRepositoryMap) {
			this.stringRepository = stringRepository;
			this.integerRepository = integerRepository;
			this.stringRepositoryArray = stringRepositoryArray;
			this.integerRepositoryArray = integerRepositoryArray;
			this.stringRepositoryList = stringRepositoryList;
			this.integerRepositoryList = integerRepositoryList;
			this.stringRepositoryMap = stringRepositoryMap;
			this.integerRepositoryMap = integerRepositoryMap;
		}
	}


	/**
	 * Pseudo-implementation of EasyMock's {@code MocksControl} class.
	 */
	public static class MocksControl {

		@SuppressWarnings("unchecked")
		public <T> T createMock(Class<T> toMock) {
			return (T) Proxy.newProxyInstance(AutowiredAnnotationBeanPostProcessorTests.class.getClassLoader(), new Class<?>[]{toMock},
					new InvocationHandler() {
						@Override
						public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
							throw new UnsupportedOperationException("mocked!");
						}
					});
		}
	}

}
