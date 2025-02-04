/*
 * Copyright (C) 2010, Emergya (http://www.emergya.es)
 *
 * @author <a href="mailto:jlrodriguez@emergya.es">Juan Luís Rodríguez</a>
 * @author <a href="mailto:marias@emergya.es">María Arias</a>
 *
 * This file is part of GoFleet
 *
 * This software is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * As a special exception, if you link this library with other files to
 * produce an executable, this library does not by itself cause the
 * resulting executable to be covered by the GNU General Public License.
 * This exception does not however invalidate any other reasons why the
 * executable file might be covered by the GNU General Public License.
 */
package es.emergya.consultas.test;

import org.appfuse.dao.BaseDaoTestCase;
import org.junit.Test;
import org.springframework.transaction.annotation.Transactional;

import es.emergya.consultas.ConfiguracionConsultas;

public class ConfiguracionConsultasTest extends BaseDaoTestCase {

	@Test
	@Transactional
	public void testGetNull() throws Exception {
		assertNull(ConfiguracionConsultas.getString("NULL"));
		assertNull(ConfiguracionConsultas.getInteger("NULL"));
		assertEquals(new Integer(1), ConfiguracionConsultas.getInteger("NULL",
				new Integer(1)));
		assertEquals("NULL", ConfiguracionConsultas.getString("NULL", "NULL"));
	}
	

	@Test
	@Transactional
	public void testGetInteger() throws Exception {
		assertNotNull(ConfiguracionConsultas.getInteger("TTL_HISTORICO_GPS"));
		assertNotNull(ConfiguracionConsultas.getString("TTL_HISTORICO_GPS"));
	}
}
