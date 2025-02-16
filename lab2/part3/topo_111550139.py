from mininet.topo import Topo

class Lab2_Topo_111550139(Topo):
    def __init__(self):
        Topo.__init__(self)

        # add host
        h1 = self.addHost('h1')
        h2 = self.addHost('h2')
        h3 = self.addHost('h3')
        
        #add switches
        s1 = self.addSwitch('s1')
        s2 = self.addSwitch('s2')
        s3 = self.addSwitch('s3')
       
        # add links
        self.addLink(s1, s2)
        self.addLink(s2, s3)
        self.addLink(s1, s3)
        self.addLink(s1, h1)
        self.addLink(s2, h2)
        self.addLink(s3, h3)

topos = {'topo_part3_111550139' : Lab2_Topo_111550139}
