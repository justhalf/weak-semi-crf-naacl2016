/** Statistical Natural Language Processing System
    Copyright (C) 2014-2016  Lu, Wei

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
/**
 * 
 */
package com.statnlp.example.linear_crf;

import com.statnlp.hybridnetworks.LocalNetworkParam;
import com.statnlp.hybridnetworks.NetworkCompiler;
import com.statnlp.hybridnetworks.TableLookupNetwork;

/**
 * @author wei_lu
 *
 */
public class LinearCRFNetwork extends TableLookupNetwork{
	
	private static final long serialVersionUID = -269366520766930758L;
	
	private int _numNodes = -1;
	
	public LinearCRFNetwork(){
		
	}
	
	public LinearCRFNetwork(int networkId, LinearCRFInstance inst, LocalNetworkParam param, NetworkCompiler compiler){
		super(networkId, inst, param, compiler);
	}

	public LinearCRFNetwork(int networkId, LinearCRFInstance inst, long[] nodes, int[][][] children, LocalNetworkParam param, int numNodes, NetworkCompiler compiler){
		super(networkId, inst, nodes, children, param, compiler);
		this._numNodes = numNodes;
	}
	
	@Override
	public int countNodes(){
		if(this._numNodes==-1)
			return super.countNodes();
		return this._numNodes;
	}
	
	//remove the node k from the network.
//	@Override
	public void remove(int k){
		//DO NOTHING..
	}
	
	//check if the node k is removed from the network.
//	@Override
	public boolean isRemoved(int k){
		return false;
	}
	
}
