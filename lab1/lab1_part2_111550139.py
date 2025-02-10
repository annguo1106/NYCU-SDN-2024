from mininet.topo import Topo

class Lab1_Topo_111550139(Topo):
    def __init__(self):
        Topo.__init__(self)

        # add host
        h1 = self.addHost('h1')
        h2 = self.addHost('h2')
        h3 = self.addHost('h3')
        h4 = self.addHost('h4')
        h5 = self.addHost('h5')

        # add switches
        s1 = self.addSwitch('s1')
        s2 = self.addSwitch('s2')
        s3 = self.addSwitch('s3')
        s4 = self.addSwitch('s4')

        # add links
        self.addLink(s4, h4)
        self.addLink(s4, h5)
        self.addLink(s4, s2)
        self.addLink(s2, s1)
        self.addLink(s2, s3)
        self.addLink(s2, h2)
        self.addLink(s1, h1)
        self.addLink(s3, h3)

topos = {'topo_part2_111550139' : Lab1_Topo_111550139}